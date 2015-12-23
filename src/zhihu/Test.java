package zhihu;

import java.io.File;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.lionsoul.jcseg.analyzer.v4x.JcsegAnalyzer4X;
import org.lionsoul.jcseg.tokenizer.core.JcsegTaskConfig;

import util.Index;

public class Test {
	//读取索引，进行检索测试
	public static void main(String args[]) throws Exception {
		// 读取索引
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

		IndexReader reader_users = DirectoryReader.open(dir_users);
		IndexReader reader_questions = DirectoryReader.open(dir_questions);
		IndexReader reader_answers = DirectoryReader.open(dir_answers);

		Index.queryTest(reader_users, reader_questions, reader_answers, analyzer);

	}

}
