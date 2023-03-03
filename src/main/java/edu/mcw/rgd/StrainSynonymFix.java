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
 * A program to replace "||" and "," separators into ; in ALIASES table for old_strain_symbol and old_strain_name
 */
public class StrainSynonymFix {

    Logger log = LogManager.getLogger("status");

    private String version;

    public static void main(String[] args) throws Exception {

        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        new XmlBeanDefinitionReader(bf).loadBeanDefinitions(new FileSystemResource("properties/AppConfigure.xml"));
        StrainSynonymFix manager = (StrainSynonymFix) (bf.getBean("manager"));

        try {
            manager.run();
        } catch (Exception e) {
            Utils.printStackTrace(e, manager.log);
            throw e;
        }
    }

    public void run() throws Exception {

        long time0 = System.currentTimeMillis();

        Dao dao = new Dao();

        log.info(getVersion());
        log.info("   "+dao.getConnectionInfo());
        SimpleDateFormat sdt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        log.info("   started at "+sdt.format(new Date(time0)));

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

        log.info("=== OK === elapsed "+ Utils.formatElapsedTime(time0, System.currentTimeMillis()));
        log.info("");
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }
}
