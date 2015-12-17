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

public class ZhihuAnswers {
	public static void main(String args[]) throws Exception {
		//System.setOut(new PrintStream(new FileOutputStream("output_Answers.txt")));

		String db = "python_test";
		String table = "answers";

		Mysql lucene = new Mysql(db);
		Connection conn = lucene.getConnection();
		// 把lucene的索引文件保存到硬盘
		String path = "/Users/Z/Git/Java/bin/zhihu/index_answers";
		Directory dir = FSDirectory.open(new File(path));
		// lucene分词器 使用Jcseg 写index和检索时使用
		Analyzer analyzer = new JcsegAnalyzer4X(JcsegTaskConfig.COMPLEX_MODE);
		JcsegAnalyzer4X jcseg = (JcsegAnalyzer4X) analyzer;
		JcsegTaskConfig config = jcseg.getTaskConfig();
		config.setAppendCJKSyn(true);
		// 初始化写入配置
		String sql = "SELECT * FROM " + table;
		//Index.indexWrite(dir, analyzer, conn, sql);
		long startTime = System.currentTimeMillis();
		IndexReader reader = DirectoryReader.open(dir);
		//Index.printAllIndexedTerms(reader);
		long endTime = System.currentTimeMillis();
		System.out.println("加载索引用时： " + (endTime - startTime) / 1000.0 + "s");
		
	}

}
