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
    private static final String DB_URL = "jdbc:mysql://localhost:3306/demo_omnichannel71";
    private static final String USER = "root";
    private static final String PASS = "root";
    private static Log _log = LogFactoryUtil.getLog(DBConnectionUtil.class);

    static
    {
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
