package util;


import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Mysql {
	static Connection conn = null;
	private final String ini = "jdbc:mysql://127.0.0.1:3306/";

	public Mysql(String dbname) {
		String url = ini + dbname;
		try {
			Class.forName("com.mysql.jdbc.Driver");
			String user = "root";
			String pwd = "19930611a";
			conn = DriverManager.getConnection(url, user, pwd);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public Connection getConnection() {
		return Mysql.conn;
	}// 获取数据库连接

	public void close() {
		try {
			Mysql.conn.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}// 关闭数据库连接

}
