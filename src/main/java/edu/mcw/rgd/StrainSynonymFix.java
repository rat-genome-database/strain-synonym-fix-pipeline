package edu.mcw.rgd;

import edu.mcw.rgd.datamodel.Alias;
import edu.mcw.rgd.datamodel.Strain;
import edu.mcw.rgd.datamodel.ontologyx.Term;
import edu.mcw.rgd.datamodel.ontologyx.TermSynonym;
import edu.mcw.rgd.process.MemoryMonitor;
import edu.mcw.rgd.process.Utils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.FileSystemResource;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author BBakir
 * @since Oct 22, 2008
 */
public class StrainSynonymFix {

    static Logger log = LogManager.getLogger("status");
    static Logger logDetail = LogManager.getLogger("detail");

    private String version;
    private Dao dao;

    public static void main(String[] args) throws Exception {

        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        new XmlBeanDefinitionReader(bf).loadBeanDefinitions(new FileSystemResource("properties/AppConfigure.xml"));
        StrainSynonymFix manager = (StrainSynonymFix) (bf.getBean("manager"));

        boolean fixStrainSynonyms = false;
        boolean updateOntologyFromStrains = false;

        for( String arg: args ) {

            arg = arg.trim().toLowerCase();

            switch( arg ) {
                case "--fix_strain_synonyms":
                    fixStrainSynonyms = true;
                    break;
                case "--update_ontology_from_strains":
                    updateOntologyFromStrains = true;
                    break;
            }
        }

        if( !fixStrainSynonyms && !updateOntologyFromStrains ) {
            log.info("Usage: java -jar strain-synonym-fix-pipeline.jar <action> [<action> ...]");
            log.info("  --fix_strain_synonyms            replace '||' separators with ';' in strain aliases");
            log.info("  --update_ontology_from_strains   transfer strain descriptions, synonyms and xrefs to RS ontology");
            System.exit(1);
        }

        long time0 = System.currentTimeMillis();

        log.info(manager.getVersion());
        log.info("   "+manager.getDao().getConnectionInfo());
        SimpleDateFormat sdt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        log.info("   started at "+sdt.format(new Date(time0)));

        MemoryMonitor memoryMonitor = new MemoryMonitor();
        memoryMonitor.start();
        try {
            if( fixStrainSynonyms ) {
                manager.fixStrainSynonyms();
            }
            if( updateOntologyFromStrains ) {
                manager.updateOntologyFromStrains();
            }
        } catch (Exception e) {
            Utils.printStackTrace(e, manager.log);
            throw e;
        } finally {
            memoryMonitor.stop();
            log.info(memoryMonitor.getSummary());
        }

        log.info("=== OK === elapsed "+ Utils.formatElapsedTime(time0, System.currentTimeMillis()));
        log.info("");
    }

    /// A program to replace "||" and "," separators into ; in ALIASES table for old_strain_symbol and old_strain_name
    void fixStrainSynonyms() throws Exception {

        int updateCount = 0;

        List<Alias> aliases = dao.getStrainAliases();
        for( Alias a: aliases ) {
            if( a.getValue().contains("||") ) {

                String oldAlias = a.getValue();
                String newAlias = oldAlias.replace("||", ";");
                a.setValue(newAlias);
                dao.updateAlias(a);

                log.info("  ### ALIAS updated RGD:"+a.getRgdId()+" ["+a.getTypeName()+"] OLD_NAME=["+oldAlias+"] NEW_NAME=["+newAlias+"]");
                updateCount++;
            }
        }

        log.info(" strain aliases updated: "+updateCount);
    }

    /// Transfer strain descriptions, aliases and RRRC ids to the matching RS ontology terms.
    void updateOntologyFromStrains() throws Exception {

        List<Strain> strains = dao.getActiveStrains();
        log.info("active strains: " + Utils.formatThousands(strains.size()));

        int withTerm = 0, descriptions = 0, synonyms = 0;
        int rrrcInserted = 0, rrrcConverted = 0, rrrcDeleted = 0;

        for( Strain s: strains ) {

            String termAcc = dao.getStrainOntId(s.getRgdId());
            if( termAcc == null ) {
                continue; // strain not associated with an RS ontology term
            }
            Term term = dao.getTerm(termAcc);
            if( term == null ) {
                continue;
            }
            withTerm++;

            // R1: transfer the description from the strain when the term has none
            if( isBlank(term.getDefinition()) && !isBlank(s.getDescription()) ) {
                String definition = sanitize(s.getDescription());
                term.setDefinition(definition);
                dao.updateTerm(term);
                descriptions++;
                logDetail.info("R1 DESCRIPTION  "+termAcc+" <= RGD:"+s.getRgdId()+" ["+s.getSymbol()+"]  "+definition);
            }

            List<TermSynonym> termSynonyms = dao.getTermSynonyms(termAcc);
            List<Alias> aliases = dao.getAliases(s.getRgdId());

            // names already on the term, to avoid creating duplicate synonyms
            Set<String> existingNames = new HashSet<>();
            for( TermSynonym syn: termSynonyms ) {
                existingNames.add(syn.getName());
            }

            // R2: transfer strain aliases as term synonyms; RRRC ids are handled as xrefs in R3
            for( Alias a: aliases ) {
                String name = a.getValue();
                if( isBlank(name) || existingNames.contains(name) || rrrcIdFromText(name) != null ) {
                    continue;
                }
                dao.insertTermSynonym(termAcc, name, "synonym", SYNONYM_SOURCE);
                existingNames.add(name);
                synonyms++;
                logDetail.info("R2 SYNONYM      "+termAcc+" <= RGD:"+s.getRgdId()+"  ["+a.getTypeName()+"] "+name);
            }

            // R3: reconcile RRRC ids on the term to a single canonical xref ("RRRC:" + 5-digit id).
            //     ids come from rrrc.us links (source/origination) and from RRRC-valued aliases (e.g.
            //     alternate_id). Any RRRC synonym already on the term -- whatever its type or padding --
            //     is converted to the canonical xref on the fly, and same-id duplicates are removed.

            // RRRC ids implied by the strain
            Set<Integer> strainRrrcIds = new TreeSet<>();
            addRrrcId(strainRrrcIds, s.getSource());
            addRrrcId(strainRrrcIds, s.getOrigination());
            for( Alias a: aliases ) {
                addRrrcId(strainRrrcIds, a.getValue());
            }

            // RRRC synonyms already on the term, grouped by numeric id
            Map<Integer, List<TermSynonym>> rrrcOnTerm = new TreeMap<>();
            for( TermSynonym syn: termSynonyms ) {
                Integer id = rrrcIdFromName(syn.getName());
                if( id != null ) {
                    rrrcOnTerm.computeIfAbsent(id, k -> new ArrayList<>()).add(syn);
                }
            }

            Set<Integer> allRrrcIds = new TreeSet<>(strainRrrcIds);
            allRrrcIds.addAll(rrrcOnTerm.keySet());

            for( Integer id: allRrrcIds ) {
                String canonical = "RRRC:" + String.format("%05d", id);
                List<TermSynonym> present = rrrcOnTerm.get(id);

                if( present == null ) {
                    // strain implies this id but the term has no RRRC synonym for it -> insert
                    dao.insertTermSynonym(termAcc, canonical, "xref", SYNONYM_SOURCE);
                    rrrcInserted++;
                    logDetail.info("R3 RRRC insert  "+termAcc+" <= RGD:"+s.getRgdId()+"  "+canonical);
                    continue;
                }

                // keep one entry as the canonical xref, preferring one that is already correct
                TermSynonym keep = null;
                for( TermSynonym syn: present ) {
                    if( "xref".equals(syn.getType()) && canonical.equals(syn.getName()) ) {
                        keep = syn;
                        break;
                    }
                }
                if( keep == null ) {
                    keep = present.get(0);
                }
                if( !("xref".equals(keep.getType()) && canonical.equals(keep.getName())) ) {
                    String before = keep.getType()+":"+keep.getName();
                    keep.setName(canonical);
                    keep.setType("xref");
                    dao.updateTermSynonym(keep);
                    rrrcConverted++;
                    logDetail.info("R3 RRRC convert "+termAcc+"  ["+before+"] -> xref:"+canonical);
                }
                // drop any remaining duplicates for this id
                for( TermSynonym syn: present ) {
                    if( syn != keep ) {
                        dao.deleteTermSynonym(syn);
                        rrrcDeleted++;
                        logDetail.info("R3 RRRC dedup   "+termAcc+"  removed ["+syn.getType()+":"+syn.getName()+"]");
                    }
                }
            }
        }

        log.info("strains linked to an RS term:   " + Utils.formatThousands(withTerm));
        log.info("R1 descriptions transferred:    " + Utils.formatThousands(descriptions));
        log.info("R2 alias synonyms inserted:     " + Utils.formatThousands(synonyms));
        log.info("R3 RRRC xrefs inserted:         " + Utils.formatThousands(rrrcInserted));
        log.info("R3 RRRC xrefs converted:        " + Utils.formatThousands(rrrcConverted));
        log.info("R3 RRRC duplicates removed:     " + Utils.formatThousands(rrrcDeleted));
    }

    private static final String SYNONYM_SOURCE = "RGD";

    // an RRRC id appears as an rrrc.us strain link (in source/origination) or as an "RRRC:<id>" token (in aliases/synonyms)
    private static final Pattern RRRC_LINK_PATTERN = Pattern.compile("rrrc\\.us/Strain/\\?x=(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern RRRC_TOKEN_PATTERN = Pattern.compile("RRRC:0*(\\d+)", Pattern.CASE_INSENSITIVE);

    /** the RRRC numeric id referenced anywhere in the given free text (rrrc.us link or RRRC:&lt;id&gt; token), or null. */
    static Integer rrrcIdFromText(String text) {
        if( text == null ) {
            return null;
        }
        Matcher m = RRRC_LINK_PATTERN.matcher(text);
        if( m.find() ) {
            return Integer.parseInt(m.group(1));
        }
        m = RRRC_TOKEN_PATTERN.matcher(text);
        if( m.find() ) {
            return Integer.parseInt(m.group(1));
        }
        return null;
    }

    /** the RRRC numeric id when the whole synonym name is an RRRC id (e.g. RRRC:00826, RRRC:826), or null. */
    static Integer rrrcIdFromName(String name) {
        if( name == null ) {
            return null;
        }
        Matcher m = RRRC_TOKEN_PATTERN.matcher(name.trim());
        return m.matches() ? Integer.parseInt(m.group(1)) : null;
    }

    /** add the RRRC numeric id referenced in the given text to the set, if any. */
    private static void addRrrcId(Set<Integer> ids, String text) {
        Integer id = rrrcIdFromText(text);
        if( id != null ) {
            ids.add(id);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** collapse tabs, line breaks and other whitespace runs into single spaces;
     *  ONT_TERMS.CKC_ONT_TERMS_DEF_COMMENT forbids tab/CR/LF in the term definition. */
    static String sanitize(String s) {
        return s == null ? null : s.replaceAll("\\s+", " ").trim();
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }

    public Dao getDao() {
        return dao;
    }

    public void setDao(Dao dao) {
        this.dao = dao;
    }
}
