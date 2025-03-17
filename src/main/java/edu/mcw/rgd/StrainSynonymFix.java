package edu.mcw.rgd;

import edu.mcw.rgd.datamodel.Alias;
import edu.mcw.rgd.process.Utils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.FileSystemResource;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * @author BBakir
 * @since Oct 22, 2008
 */
public class StrainSynonymFix {

    static Logger log = LogManager.getLogger("status");

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

        long time0 = System.currentTimeMillis();

        log.info(manager.getVersion());
        log.info("   "+manager.getDao().getConnectionInfo());
        SimpleDateFormat sdt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        log.info("   started at "+sdt.format(new Date(time0)));

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

    void updateOntologyFromStrains() {


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
