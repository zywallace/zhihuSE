package util;

import java.io.IOException;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.queries.CustomScoreProvider;
import org.apache.lucene.queries.CustomScoreQuery;
import org.apache.lucene.search.Query;

public class MyScore extends CustomScoreQuery {
	
	private boolean isValuable(Document doc) {
		//如果答案长度大于140，则认为是高质量回答
		if (doc.get("upvote") != null) {
			int length = doc.get("content").length();
			if (length >= 140)
				return true;
			else
				return false;
		}
		return true;
	}
	
	private boolean isVanswer(Document doc){
		if (doc.get("upvote") != null) {
			//String author_url = doc.get("author_url");
			//get author info from author url
			
		}
		return false;
	}
	
	public static boolean isHit(Document doc) {
		//如果答案upvote，问题的关注人数、回答数，用户的赞同、感谢、关注人数，其中之一是前10%的话
		if (doc.get("upvote") != null) {//doc是答案
			String u = doc.get("upvote");
			int upvote = Integer.parseInt(u);
			if (upvote >= 72)
				return true;
			else
				return false;
		}
		if (doc.get("title") != null) {//doc是问题
			String a = doc.get("answers_num");
			int answers_num = Integer.parseInt(a);
			String f = doc.get("followers_num");
			int followers_num = Integer.parseInt(f);
			if (answers_num >= 188 || followers_num >= 19)
				return true;
			else
				return false;
		}
		if (doc.get("name") != null) {//doc是用户
			String t = doc.get("thanks_num");
			int thanks_num = Integer.parseInt(t);
			String f = doc.get("followers_num");
			int followers_num = Integer.parseInt(f);
			String a = doc.get("agree_num");
			int agree_num = Integer.parseInt(a);
			if (thanks_num >= 9956 || followers_num >= 4900 || agree_num >= 9956)
				return true;
			else
				return false;
		}
		return false;
	}

	public MyScore(Query subQuery) {
		super(subQuery);
	}

	@Override
	protected CustomScoreProvider getCustomScoreProvider(AtomicReaderContext context) throws IOException {
		/**
		 * 自定义的评分provider
		 **/
		return new MyCustomScoreProvider(context);
	}

	private class MyCustomScoreProvider extends CustomScoreProvider {
		private AtomicReaderContext context = null;

		public MyCustomScoreProvider(AtomicReaderContext context) throws IOException {
			super(context);
			this.context = context;
		}

		@Override
		public float customScore(int doc, float subQueryScore, float valSrcScore) throws IOException {
			// 自定义打分器，如果是热门原有分数*1.1，如果是答案是大V回答，再乘1.1,如果是少于140的答案，则权重降到原先的1/10
			Document docAtHand = context.reader().document(doc);
			float val = 1.0f;
			if (isHit(docAtHand))
				val *= 1.1;
			if (isVanswer(docAtHand))
				val *= 1.1;
			if (!isValuable(docAtHand))
				val = 0.1f;
			return val * subQueryScore;
		}
	}
}
