package zhihu;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import util.Mysql;

public class Statistic {
	public static void main(String args[]) throws Exception {

		String db = "python_test";
		Mysql lucene = new Mysql(db);
		Connection conn = lucene.getConnection();

		String sql = "select * from questions";
		PreparedStatement ps = conn.prepareStatement(sql);
		ResultSet rs = ps.executeQuery();

		File file = new File("questions.txt");

		if (!file.exists()) {
			file.createNewFile();
		}

		FileWriter fileWritter = new FileWriter(file.getName(), true);
		BufferedWriter bufferWritter = new BufferedWriter(fileWritter);

		while (rs.next()) {
			int answer_num = rs.getInt("answers_num");
			int follower_num = rs.getInt("followers_num");

			String line = String.valueOf(answer_num) + ' ' +String.valueOf(follower_num) + '\n';
			bufferWritter.write(line);
		}
		bufferWritter.close();
	}
}
