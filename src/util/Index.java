package util;

import java.io.IOException;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Scanner;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.Fields;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.InvalidTokenOffsetsException;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleFragmenter;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.Version;
import org.lionsoul.jcseg.analyzer.v4x.JcsegAnalyzer4X;
import org.lionsoul.jcseg.tokenizer.core.JcsegTaskConfig;

public class Index {

	public static boolean isindexedField(String field) {
		String[] indexedFields = { "content", "title", "name" };
		for (String f : indexedFields) {
			if (f.equals(field))
				return true;
		}

		return false;
	}

	public static boolean isUrl(String field) {
		String[] url = { "url", "author_url", "question_url" };
		for (String f : url) {
			if (f.equals(field))
				return true;
		}

		return false;
	}

	public static void indexWrite(Directory dir, Analyzer analyzer, Connection conn, String sql)
			throws IOException, SQLException {
		long startTime = System.currentTimeMillis();

		IndexWriterConfig iwc = new IndexWriterConfig(Version.LUCENE_47, analyzer);
		iwc.setOpenMode(OpenMode.CREATE);// 创建模式 OpenMode.CREATE_OR_APPEND 添加模式
		// 如果是CREATE ,每次都会重新创建这个索引，清空以前的数据，如果是append 每次都会追加，之前的不删除
		// 在日常的需求索引添加中，一般都是 APPEND 持续添加模式
		IndexWriter writer = new IndexWriter(dir, iwc);
		PreparedStatement ps = conn.prepareStatement(sql);
		ResultSet rs = ps.executeQuery();

		ResultSetMetaData metaData = rs.getMetaData();
		int count = metaData.getColumnCount(); // number of column
		String fields[] = new String[count];

		for (int i = 1; i <= count; i++) {
			fields[i - 1] = metaData.getColumnLabel(i);
		}

		while (rs.next()) {
			Document doc = new Document();
			// 该表所有的字段建立索引
			for (String field : fields) {
				if (isindexedField(field))
					doc.add(new TextField(field, rs.getString(field), Field.Store.YES));
				else if (isUrl(field))
					doc.add(new StringField(field, rs.getString(field), Field.Store.YES));
				else
					doc.add(new StoredField(field, rs.getInt(field)));
			}

			writer.addDocument(doc);
		}
		rs.close(); // 关闭记录集
		conn.close(); // 关闭数据库连接
		// writer.optimize(); //索引优化
		writer.close(); // 关闭读写器
		long endTime = System.currentTimeMillis();
		System.out.println("建立索引用时： " + (endTime - startTime) / 1000.0 + "s");
	}

	public static void printAllIndexedTerms(IndexReader reader) throws IOException {
		long startTime = System.currentTimeMillis();

		Fields fields = MultiFields.getFields(reader);
		Terms terms = fields.terms("content");
		TermsEnum termsEnum = terms.iterator(null);
		BytesRef text;
		int word_count = 0;
		for (String field : fields) {
			if (isindexedField(field)) {
				while ((text = termsEnum.next()) != null) {
					String termText = text.utf8ToString();
					Term termInstance = new Term("content", text);
					long termFreq = reader.totalTermFreq(termInstance);
					long docCount = reader.docFreq(termInstance);
					word_count++;
					System.out.println("text = " + termText + "; term freq = " + termFreq + "; doc freq = " + docCount);
				}
			}
		}
		System.out.println("共有" + word_count + "个词");
		long endTime = System.currentTimeMillis();
		System.out.println("遍历用时： " + (endTime - startTime) / 1000.0 + "s");
	}

	public static void queryHighlight(IndexReader reader, Analyzer analyzer)
			throws ParseException, IOException, InvalidTokenOffsetsException {

		Scanner sc = new Scanner(System.in);

		while (true) {
			System.out.println("请输入你的检索式：");

			String querystr = sc.nextLine();
			if (querystr.equals(":quit")) {
				break;
			}
			long startTime = System.currentTimeMillis();
			Query q = new QueryParser(Version.LUCENE_47, "content", analyzer).parse(querystr);

			int hitsPerPage = 10;
			IndexSearcher searcher = new IndexSearcher(reader);

			TopDocs docs = searcher.search(q, hitsPerPage);
			ScoreDoc[] hits = docs.scoreDocs;

			Highlighter highlighter = null;
			SimpleHTMLFormatter simpleHTMLFormatter = new SimpleHTMLFormatter("<read>", "</read>");
			highlighter = new Highlighter(simpleHTMLFormatter, new QueryScorer(q));
			// 这个100是指定关键字字符串的context的长度，你可以自己设定，因为不可能返回整篇正文内容
			highlighter.setTextFragmenter(new SimpleFragmenter(30));

			System.out.println("Found " + hits.length + " hits.");
			for (int i = 0; i < hits.length; i++) {
				int docId = hits[i].doc;
				float docValue = hits[i].score;
				Document d = searcher.doc(docId);

				System.out.println((i + 1) + ". " + d.get("url") + '\t' + docValue);
				String content = d.get("content");
				TokenStream tokenStream = new JcsegAnalyzer4X(JcsegTaskConfig.COMPLEX_MODE).tokenStream("token",
						new StringReader(content));
				System.out.println(highlighter.getBestFragment(tokenStream, content));

			}

			long endTime = System.currentTimeMillis();
			System.out.println("检索用时： " + (endTime - startTime) / 1000.0 + "s");
		}
		sc.close();
	}

	public static void queryTest(IndexReader reader_users, IndexReader reader_questions, IndexReader reader_answers,
			Analyzer analyzer) throws ParseException, IOException, InvalidTokenOffsetsException {

		Scanner sc = new Scanner(System.in);

		while (true) {
			System.out.println("请输入你的检索式：");

			String querystr = sc.nextLine();
			if (querystr.equals(":quit")) {
				break;
			}
			long startTime = System.currentTimeMillis();

			searchUsers(reader_users, analyzer, querystr);
			searchQuestions(reader_questions, analyzer, querystr);
			searchAnswers(reader_answers, analyzer, querystr);

			long endTime = System.currentTimeMillis();
			System.out.println("检索用时： " + (endTime - startTime) / 1000.0 + "s");
		}
		sc.close();
	}

	public static void searchUsers(IndexReader reader, Analyzer analyzer, String querystr)
			throws ParseException, IOException, InvalidTokenOffsetsException {
		Query q = new QueryParser(Version.LUCENE_47, "name", analyzer).parse(querystr);

		int hitsPerPage = 3;
		IndexSearcher searcher = new IndexSearcher(reader);

		TopDocs docs = searcher.search(q, hitsPerPage);
		ScoreDoc[] hits = docs.scoreDocs;

		System.out.println("Found " + hits.length + " users.");
		for (int i = 0; i < hits.length; i++) {
			int docId = hits[i].doc;
			float docValue = hits[i].score;
			Document d = searcher.doc(docId);
			System.out.println((i + 1) + ". " + d.get("url") + '\t' + d.get("name") + '\t' + docValue);

		}

	}

	public static void searchAnswers(IndexReader reader, Analyzer analyzer, String querystr)
			throws ParseException, IOException, InvalidTokenOffsetsException {
		Query q = new QueryParser(Version.LUCENE_47, "content", analyzer).parse(querystr);

		int hitsPerPage = 5;
		IndexSearcher searcher = new IndexSearcher(reader);

		TopDocs docs = searcher.search(q, hitsPerPage);
		ScoreDoc[] hits = docs.scoreDocs;

		System.out.println("Found " + hits.length + " answers.");
		for (int i = 0; i < hits.length; i++) {
			int docId = hits[i].doc;
			float docValue = hits[i].score;
			Document d = searcher.doc(docId);

			System.out.println((i + 1) + ". " + d.get("url") + '\t' + docValue);
			String content = d.get("content");
			int endIndex = 30 > content.length() ? content.length() : 30;
			String snippet = content.substring(0, endIndex);
			System.out.println(snippet);

		}

	}

	public static void searchQuestions(IndexReader reader, Analyzer analyzer, String querystr)
			throws ParseException, IOException, InvalidTokenOffsetsException {
		Query q = new QueryParser(Version.LUCENE_47, "title", analyzer).parse(querystr);

		int hitsPerPage = 10;
		IndexSearcher searcher = new IndexSearcher(reader);

		TopDocs docs = searcher.search(q, hitsPerPage);
		ScoreDoc[] hits = docs.scoreDocs;

		System.out.println("Found " + hits.length + " questions.");
		for (int i = 0; i < hits.length; i++) {
			int docId = hits[i].doc;
			float docValue = hits[i].score;
			Document d = searcher.doc(docId);

			System.out.println((i + 1) + ". " + d.get("url") + '\t' + docValue);
			System.out.println(d.get("title"));
			String content = d.get("content");
			if (content.length() != 0) {
				int endIndex = 50 > content.length() ? content.length() : 50;
				String snippet = content.substring(0, endIndex);
				System.out.println(snippet);
			} else {
				System.out.println("no desc");
			}

		}
	}
}
