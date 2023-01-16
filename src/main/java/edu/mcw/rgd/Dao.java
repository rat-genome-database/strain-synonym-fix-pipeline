package edu.mcw.rgd;

import edu.mcw.rgd.dao.impl.AliasDAO;
import edu.mcw.rgd.datamodel.Alias;

import java.util.ArrayList;
import java.util.List;

public class Dao {
    private AliasDAO aliasDAO = new AliasDAO();

    public String getConnectionInfo() {
        return aliasDAO.getConnectionInfo();
    }

    public List<Alias> getStrainAliases() throws Exception {

        List<Alias> aliases = new ArrayList<>();
        String[] aliasTypes = new String[]{"old_strain_symbol","old_strain_name"};
        for( String aliasType: aliasTypes ) {
            aliases.addAll(aliasDAO.getAliasesByType(aliasType);
        }
        return aliases;
    }

    public void updateAlias(Alias a) throws Exception {
        aliasDAO.updateAlias(a);
    }
}
