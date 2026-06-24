package edu.mcw.rgd;

import edu.mcw.rgd.dao.impl.AliasDAO;
import edu.mcw.rgd.dao.impl.OntologyXDAO;
import edu.mcw.rgd.dao.impl.StrainDAO;
import edu.mcw.rgd.datamodel.Alias;
import edu.mcw.rgd.datamodel.Strain;
import edu.mcw.rgd.datamodel.ontologyx.Term;
import edu.mcw.rgd.datamodel.ontologyx.TermSynonym;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Dao {
    private final AliasDAO aliasDAO = new AliasDAO();
    private final StrainDAO strainDAO = new StrainDAO();
    private final OntologyXDAO ontologyXDAO = new OntologyXDAO();

    public String getConnectionInfo() {
        return aliasDAO.getConnectionInfo();
    }

    // ---- support for --fix_strain_synonyms ----

    public List<Alias> getStrainAliases() throws Exception {

        List<Alias> aliases = new ArrayList<>();
        String[] aliasTypes = new String[]{"old_strain_symbol","old_strain_name"};
        for( String aliasType: aliasTypes ) {
            aliases.addAll(aliasDAO.getAliasesByType(aliasType));
        }
        return aliases;
    }

    public void updateAlias(Alias a) throws Exception {
        aliasDAO.updateAlias(a);
    }

    // ---- support for --update_ontology_from_strains ----

    public List<Strain> getActiveStrains() throws Exception {
        return strainDAO.getActiveStrains();
    }

    /** RS ontology term acc associated with a strain rgd id, or null if the strain has no ontology term. */
    public String getStrainOntId(int rgdId) throws Exception {
        return ontologyXDAO.getStrainOntIdForRgdId(rgdId);
    }

    public Term getTerm(String termAcc) throws Exception {
        return ontologyXDAO.getTermByAccId(termAcc);
    }

    public void updateTerm(Term term) throws Exception {
        ontologyXDAO.updateTerm(term);
    }

    /** update an existing term synonym (identified by its key), e.g. to change its type. */
    public void updateTermSynonym(TermSynonym syn) throws Exception {
        ontologyXDAO.updateTermSynonym(syn);
    }

    /** delete a term synonym (identified by its key). */
    public void deleteTermSynonym(TermSynonym syn) throws Exception {
        ontologyXDAO.deleteTermSynonyms(List.of(syn));
    }

    /** all aliases for a strain rgd id. */
    public List<Alias> getAliases(int rgdId) throws Exception {
        return aliasDAO.getAliases(rgdId);
    }

    public List<TermSynonym> getTermSynonyms(String termAcc) throws Exception {
        return ontologyXDAO.getTermSynonyms(termAcc);
    }

    /** insert a term synonym; returns the new synonym key. */
    public int insertTermSynonym(String termAcc, String name, String type, String source) throws Exception {
        TermSynonym syn = new TermSynonym();
        syn.setTermAcc(termAcc);
        syn.setName(name);
        syn.setType(type);
        syn.setSource(source);
        Date now = new Date();
        syn.setCreatedDate(now);
        syn.setLastModifiedDate(now);
        return ontologyXDAO.insertTermSynonym(syn);
    }
}
