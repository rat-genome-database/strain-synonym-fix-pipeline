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
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

    /// Transfer strain descriptions, aliases and RRRC links to the matching RS ontology terms.
    void updateOntologyFromStrains() throws Exception {

        List<Strain> strains = dao.getActiveStrains();
        log.info("active strains: " + Utils.formatThousands(strains.size()));

        int withTerm = 0, descriptions = 0, synonyms = 0, rrrcXrefs = 0;

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
                term.setDefinition(s.getDescription());
                dao.updateTerm(term);
                descriptions++;
                logDetail.info("R1 DESCRIPTION  "+termAcc+" <= RGD:"+s.getRgdId()+" ["+s.getSymbol()+"]  "+s.getDescription());
            }

            // names already on the term, to avoid creating duplicates
            Set<String> existingNames = new HashSet<>();
            for( TermSynonym syn: dao.getTermSynonyms(termAcc) ) {
                existingNames.add(syn.getName());
            }

            // R2: transfer strain aliases as term synonyms
            for( Alias a: dao.getAliases(s.getRgdId()) ) {
                String name = a.getValue();
                if( isBlank(name) || existingNames.contains(name) ) {
                    continue;
                }
                dao.insertTermSynonym(termAcc, name, "synonym", SYNONYM_SOURCE);
                existingNames.add(name);
                synonyms++;
                logDetail.info("R2 SYNONYM      "+termAcc+" <= RGD:"+s.getRgdId()+"  ["+a.getTypeName()+"] "+name);
            }

            // R3: create an RRRC xref when the strain links to the Rat Resource and Research Center
            String rrrcId = extractRrrcId(s.getSource(), s.getOrigination());
            if( rrrcId != null ) {
                String xref = "RRRC:"+rrrcId;
                if( !existingNames.contains(xref) ) {
                    dao.insertTermSynonym(termAcc, xref, "xref", SYNONYM_SOURCE);
                    existingNames.add(xref);
                    rrrcXrefs++;
                    logDetail.info("R3 RRRC XREF    "+termAcc+" <= RGD:"+s.getRgdId()+"  "+xref);
                }
            }
        }

        log.info("strains linked to an RS term:   " + Utils.formatThousands(withTerm));
        log.info("R1 descriptions transferred:    " + Utils.formatThousands(descriptions));
        log.info("R2 alias synonyms inserted:     " + Utils.formatThousands(synonyms));
        log.info("R3 RRRC xrefs inserted:         " + Utils.formatThousands(rrrcXrefs));
    }

    private static final String SYNONYM_SOURCE = "RGD";

    // a strain links to the RRRC via an <a href=...rrrc.us/Strain/?x=NNN...> tag in source or origination
    private static final Pattern RRRC_PATTERN = Pattern.compile("rrrc\\.us/Strain/\\?x=(\\d+)", Pattern.CASE_INSENSITIVE);

    /** extract the RRRC strain id from the first field containing an rrrc.us strain link, or null. */
    static String extractRrrcId(String... fields) {
        for( String f: fields ) {
            if( f == null ) {
                continue;
            }
            Matcher m = RRRC_PATTERN.matcher(f);
            if( m.find() ) {
                return m.group(1);
            }
        }
        return null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
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
