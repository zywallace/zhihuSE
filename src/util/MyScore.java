package util;

import java.io.IOException;

import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queries.CustomScoreProvider;
import org.apache.lucene.queries.CustomScoreQuery;
import org.apache.lucene.search.FieldCache;
import org.apache.lucene.search.Query;
import org.apache.lucene.queries.function.*;;

public class MyScore extends CustomScoreQuery {
	
	public MyScore(Query subQuery) {
		super(subQuery);
	}

	@Override
	public CustomScoreProvider getCustomScoreProvider(final AtomicReaderContext context) throws IOException {
		return new MyCustomScoreProvider(context);
	}

	public class MyCustomScoreProvider extends CustomScoreProvider {
		int[] upvote = null;

		private AtomicReader atomicReader;

		public MyCustomScoreProvider(AtomicReaderContext context) {
			super(context);
			atomicReader = context.reader();
			try {
				upvote = FieldCache.DEFAULT_INT_PARSER(context, "filename");
			} catch (IOException e) {
				e.printStackTrace();
			}

		}

		@Override
		public float customScore(int doc, float subQueryScore, float valSrcScore) throws IOException {
			return 
		}
	}

}