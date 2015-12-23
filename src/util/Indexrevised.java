package util;

import java.io.IOException;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

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
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
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
import org.apache.lucene.queryparser.classic.QueryParserBase;

public class Indexrevised {

	public static boolean isindexedField(String field) {
		// 判断是否是需要全文检索即生成index的field
		String[] indexedFields = { "content", "title", "name" };
		for (String f : indexedFields) {
			if (f.equals(field))
				return true;
		}
		return false;
	}

	public static boolean isUrl(String field) {
		// 判断是否是url
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
		// 索引：创建模式 OpenMode.CREATE_OR_APPEND 添加模式
		// 如果是CREATE ,每次都会重新创建这个索引，清空以前的数据，如果是append 每次都会追加，之前的不删除
		// 写在dir的path上
		IndexWriterConfig iwc = new IndexWriterConfig(Version.LUCENE_47, analyzer);
		iwc.setOpenMode(OpenMode.CREATE);
		IndexWriter writer = new IndexWriter(dir, iwc);
		// 执行sql语句，取出数据库内数据，
		PreparedStatement ps = conn.prepareStatement(sql);
		ResultSet rs = ps.executeQuery();
		// 得到索引document中需要写入（store）的field
		ResultSetMetaData metaData = rs.getMetaData();
		int count = metaData.getColumnCount(); // number of column
		String fields[] = new String[count];
		for (int i = 1; i <= count; i++) {
			fields[i - 1] = metaData.getColumnLabel(i);
		}
		// 写入
		while (rs.next()) {
			Document doc = new Document();
			// 为该结果集字段建立索引
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
		// 遍历存储表
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

	@SuppressWarnings("resource")
	public static void queryHighlight(IndexReader reader, Analyzer analyzer)
			throws ParseException, IOException, InvalidTokenOffsetsException {
		// 测试中，将输出的命中结果中，query字段标红
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
			highlighter.setTextFragmenter(new SimpleFragmenter(100));

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
		// 测试类，输入:quit退出。检索用户、问题、答案
		Scanner sc = new Scanner(System.in);
		while (true) {
			System.out.println("请输入你的检索式：");
			String querystr = sc.nextLine();
			if (querystr.equals(":quit"))
				break;

			long startTime = System.currentTimeMillis();
			searchUsers(reader_users, analyzer, querystr);
			Set<String> questions_urls = searchQuestions(reader_questions, analyzer, querystr);
			searchAnswers(reader_answers, analyzer, querystr, questions_urls);
			long endTime = System.currentTimeMillis();
			System.out.println("检索用时： " + (endTime - startTime) / 1000.0 + "s");
		}
		sc.close();
	}

	public static void searchUsers(IndexReader reader, Analyzer analyzer, String querystr)
			throws ParseException, IOException, InvalidTokenOffsetsException {
		// 在名字进行查找
		QueryParser queryParser = new QueryParser(Version.LUCENE_47, "name", analyzer);
		queryParser.setDefaultOperator(QueryParser.Operator.AND);
		Query q = queryParser.parse(querystr);
		Query customQuery = new MyScore(q);
		// 找到前5个用户
		int hitsPerPage = 5;
		IndexSearcher searcher = new IndexSearcher(reader);
		TopDocs docs = searcher.search(customQuery, hitsPerPage);
		ScoreDoc[] hits = docs.scoreDocs;
		// 输出找到用户的url+name
		Highlighter highlighter = null;
		SimpleHTMLFormatter simpleHTMLFormatter = new SimpleHTMLFormatter(Print.ANSI_RED, Print.ANSI_RESET);
		highlighter = new Highlighter(simpleHTMLFormatter, new QueryScorer(q));
		// 这个100是指定关键字字符串的context的长度，你可以自己设定，因为不可能返回整篇正文内容
		highlighter.setTextFragmenter(new SimpleFragmenter(140));
		
		Print.printRedln("Found " + hits.length + " users.\n");
		for (int i = 0; i < hits.length; i++) {
			int docId = hits[i].doc;
			// float docValue = hits[i].score;
			Document d = searcher.doc(docId);
			System.out.println((i + 1) + ". " + d.get("url") );
			String name = d.get("name");
			TokenStream tokenStreamt = new JcsegAnalyzer4X(JcsegTaskConfig.COMPLEX_MODE).tokenStream("token",
					new StringReader(name));
			System.out.println(highlighter.getBestFragment(tokenStreamt, name));
			if (MyScore.isHit(d)) {
				System.out.println("该用户可能是大V哦~");
			}
			System.out.println();
		}
	}
	

	public static void searchAnswers(IndexReader reader, Analyzer analyzer, String querystr, Set<String> questions_urls)
			throws ParseException, IOException, InvalidTokenOffsetsException {
		// 进行single field查询，在content上，找到前20个结果。
		QueryParser queryParser = new QueryParser(Version.LUCENE_47, "content", analyzer);
		queryParser.setDefaultOperator(QueryParser.Operator.AND);
		Query q = queryParser.parse(querystr);
		Query customQuery = new MyScore(q);

		int hitsPerPage = 10;
		IndexSearcher searcher = new IndexSearcher(reader);
		TopDocs docs = searcher.search(customQuery, hitsPerPage);
		ScoreDoc[] hits = docs.scoreDocs;
		
		Highlighter highlighter = null;
		SimpleHTMLFormatter simpleHTMLFormatter = new SimpleHTMLFormatter(Print.ANSI_RED, Print.ANSI_RESET);
		highlighter = new Highlighter(simpleHTMLFormatter, new QueryScorer(q));
		// 这个100是指定关键字字符串的context的长度，你可以自己设定，因为不可能返回整篇正文内容
		highlighter.setTextFragmenter(new SimpleFragmenter(140));
		
		Print.printRedln("Found " + hits.length + " answers.\n");
		for (int i = 0; i < hits.length; i++) {
			int docId = hits[i].doc;
			// float docValue = hits[i].score; 该文档的命中得分
			Document d = searcher.doc(docId);
			String url = d.get("url");
			System.out.println((i + 1) + ". " + url);
			String content = d.get("content");
			TokenStream tokenStream = new JcsegAnalyzer4X(JcsegTaskConfig.COMPLEX_MODE).tokenStream("token",
					new StringReader(content));
			System.out.println(highlighter.getBestFragment(tokenStream, content));
			
			/***
			int endIndex = 140 > content.length() ? content.length() : 140;
			String snippet = content.substring(0, endIndex);
			System.out.println(snippet);
			System.out.println();
			***/
		}
		/***
		 * 与问题进行比对，如果该答案的question_url出现在questions_url中，则输出该答案，否则则不输出。
		 * 如果没有question命中，那么输出所有命中答案。 输出格式为回答的url，内容的前140个字，upvote数量
		 * 
		 * if (questions_urls.size()< 1) { System.out.println("Found " +
		 * hits.length + " answers."); for (int i = 0; i < hits.length; i++) {
		 * int docId = hits[i].doc; // float docValue = hits[i].score; 该文档的命中得分
		 * Document d = searcher.doc(docId); System.out.println((i + 1) + ". " +
		 * d.get("url")); String content = d.get("content"); int endIndex = 140
		 * > content.length() ? content.length() : 140; String snippet =
		 * content.substring(0, endIndex); System.out.println(snippet);
		 * System.out.println(); } } else{ int related_num=0;
		 * System.out.println(
		 * "Finding any answer related to these questions……\n"); for (int i = 0;
		 * i < hits.length; i++) { int docId = hits[i].doc; // float docValue =
		 * hits[i].score; 该文档的命中得分 Document d = searcher.doc(docId); String
		 * url=d.get("question_url"); if(questions_urls.contains(url)){
		 * related_num++; System.out.println((related_num + 1) + ". " +
		 * d.get("url")); String content = d.get("content"); int endIndex = 140
		 * > content.length() ? content.length() : 140; String snippet =
		 * content.substring(0, endIndex); System.out.println(snippet);
		 * System.out.println(); } } if (related_num<1) System.out.println(
		 * "Found no relevant answer."); else System.out.println("Found " +
		 * related_num + " answers for these questions."); }
		 ***/
	}

	public static Set<String> searchQuestions(IndexReader reader, Analyzer analyzer, String querystr)
			throws ParseException, IOException, InvalidTokenOffsetsException {
		// 定义问题检索field，为问题的title和content，并且设置权重为10：4。
		String[] fields = { "title", "content" };
		HashMap<String, Float> boosts = new HashMap<String, Float>();
		boosts.put("title", 10f);
		boosts.put("content", 4f);
		MultiFieldQueryParser multiFieldQueryParser = new MultiFieldQueryParser(Version.LUCENE_47, fields, analyzer,
				boosts);
		multiFieldQueryParser.setDefaultOperator(QueryParserBase.AND_OPERATOR);
		Query query = multiFieldQueryParser.parse(querystr);
		Query customQuery = new MyScore(query);
		// 找到前20个命中的结果
		int hitsPerPage = 10;
		IndexSearcher searcher = new IndexSearcher(reader);
		TopDocs docs = searcher.search(customQuery, hitsPerPage);
		ScoreDoc[] hits = docs.scoreDocs;
		// 结果输出前10个，输出找的的问题的url、标题、加上问题描述的前140个字
		Print.printRedln("Found " + hits.length + " questions.\n");
		
		Highlighter highlighter = null;
		SimpleHTMLFormatter simpleHTMLFormatter = new SimpleHTMLFormatter(Print.ANSI_RED, Print.ANSI_RESET);
		highlighter = new Highlighter(simpleHTMLFormatter, new QueryScorer(query));
		// 这个100是指定关键字字符串的context的长度，你可以自己设定，因为不可能返回整篇正文内容
		highlighter.setTextFragmenter(new SimpleFragmenter(140));
		
		Set<String> urls = new HashSet<String>();
		for (int i = 0; i < hits.length; i++) {
			int docId = hits[i].doc;
			// float docValue = hits[i].score; 该文档的命中得分
			Document d = searcher.doc(docId);
			String url = d.get("url");
			urls.add(url);
			System.out.println((i + 1) + ". " + url);
			
			String title = d.get("title");
			TokenStream tokenStreamt = new JcsegAnalyzer4X(JcsegTaskConfig.COMPLEX_MODE).tokenStream("token",
					new StringReader(title));
			System.out.println(highlighter.getBestFragment(tokenStreamt, title));
			
			String content = d.get("content");
			if (content.length() != 0) {
				TokenStream tokenStream = new JcsegAnalyzer4X(JcsegTaskConfig.COMPLEX_MODE).tokenStream("token",
						new StringReader(content));
				System.out.println(highlighter.getBestFragment(tokenStream, content));
				/***
				int endIndex = 140 > content.length() ? content.length() : 140;
				String snippet = content.substring(0, endIndex);
				System.out.println(snippet);
				***/
			} else {
				System.out.println("#没有问题描述");
			}
			System.out.println();
		}
		return urls;
	}
}
