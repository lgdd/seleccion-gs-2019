package mvc.portlet.util;

import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
//gk-audit-comment :- separating connection into DBConnectionUtil from the business logic
public class DBConnectionUtil {

    private static Connection conn = null;
    private static final String JDBC_DRIVER = "com.mysql.jdbc.Driver";
    private static final String DB_URL = "jdbc:mysql://localhost/FORM";
    private static final String USER = "username";
    private static final String PASS = "password";
    private static Log _log = LogFactoryUtil.getLog(DBConnectionUtil.class);

    static
    {
        String url = "jdbc:mysql:// localhost:3306/org";
        String user = "root";
        String pass = "root";
        try {
            Class.forName(JDBC_DRIVER);
            _log.info("Connecting to a selected database...");
            conn = DriverManager.getConnection(DB_URL, USER, PASS);
            _log.info("Connected database successfully...");
            _log.info("Creating statement...");
        }
        catch (ClassNotFoundException | SQLException e) {
            _log.error(e.getLocalizedMessage());
        }
    }
    public static Connection getConnection()
    {
        return conn;
    }


}
