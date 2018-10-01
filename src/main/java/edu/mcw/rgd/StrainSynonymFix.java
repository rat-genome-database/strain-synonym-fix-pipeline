package edu.mcw.rgd;

import edu.mcw.rgd.dao.impl.AliasDAO;

import java.sql.*;

/**
 * Created by IntelliJ IDEA.
 * User: BBakir
 * Date: Oct 22, 2008
 * Time: 1:36:39 PM
 * A program to replace "||" and "," separators into ; at ALIASES table for old_strain_symbol and old_strain_name
 */
public class StrainSynonymFix {
    public static void main(String[] args) throws Exception {

        AliasDAO aliasDAO = new AliasDAO();
        Connection conn = aliasDAO.getDataSource().getConnection();
        String updateSynonymString = "UPDATE ALIASES SET ALIAS_VALUE = ?,ALIAS_VALUE_LC=? WHERE ALIAS_KEY = ?";
        PreparedStatement updateSynonym = conn.prepareStatement(updateSynonymString);
        Statement stmt = conn.createStatement(
                ResultSet.TYPE_SCROLL_INSENSITIVE,
                ResultSet.CONCUR_UPDATABLE);
        ResultSet rs = stmt.executeQuery("select a.ALIAS_KEY,a.ALIAS_VALUE from aliases a where a.ALIAS_VALUE like '%||%' and a.ALIAS_TYPE_NAME_LC in('old_strain_symbol','old_strain_name')");
        int count = 0;
        while (rs.next()) {
            int aliasKey = rs.getInt("ALIAS_KEY");
            String aliasValue = rs.getString("ALIAS_VALUE");
            String newAliasValue=aliasValue.replace("||",";");
            updateSynonym.setInt(3, aliasKey);
            updateSynonym.setString(1, newAliasValue);
            updateSynonym.setString(2, newAliasValue.toLowerCase());           
            updateSynonym.executeUpdate();
            count++;
        }

        System.out.println(count+" strain aliases have been fixed.");
    }
}
