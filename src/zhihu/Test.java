package zhihu;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.sql.Connection;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.lionsoul.jcseg.analyzer.v4x.JcsegAnalyzer4X;
import org.lionsoul.jcseg.tokenizer.core.JcsegTaskConfig;

import util.Index;
import util.Mysql;

public class Test {
	public static void main(String args[]) throws Exception {
		String db = "lucene_test";
		String table = "test2";

		Mysql lucene = new Mysql(db);
		Connection conn = lucene.getConnection();
		// 把lucene的索引文件保存到硬盘
		String path_users = "/Users/Z/Git/Java/bin/zhihu/index_users";
		Directory dir_users = FSDirectory.open(new File(path_users));

		String path_questions = "/Users/Z/Git/Java/bin/zhihu/index_questions";
		Directory dir_questions = FSDirectory.open(new File(path_questions));

		String path_answers = "/Users/Z/Git/Java/bin/zhihu/index_answers";
		Directory dir_answers = FSDirectory.open(new File(path_answers));

		// lucene分词器 使用Jcseg 写index和检索时使用
		Analyzer analyzer = new JcsegAnalyzer4X(JcsegTaskConfig.COMPLEX_MODE);
		JcsegAnalyzer4X jcseg = (JcsegAnalyzer4X) analyzer;
		JcsegTaskConfig config = jcseg.getTaskConfig();
		config.setAppendCJKSyn(true);
		// 初始化写入配置
		String sql = "SELECT * FROM " + table;
		// Index.indexWrite(dir, analyzer, conn, sql);

		IndexReader reader_users = DirectoryReader.open(dir_users);
		IndexReader reader_questions = DirectoryReader.open(dir_questions);
		IndexReader reader_answers = DirectoryReader.open(dir_answers);

		// Index.printAllIndexedTerms(reader);

		Index.queryTest(reader_users, reader_questions, reader_answers, analyzer);

	}

}
