# zhihuSE
Search engine from zhihu.com

运行test可以进行搜索，输入:quit即可退出

使用lucene 4.7.1,分词器使用 Jcseg 1.9.8

Lucene

zhihu.com的数据库在通过https://github.com/zywallace/spider_zhihu爬下来，使用mysql存储在本地

索引结构：
1对于用户，检索式不会被分词处理，使用的是类似于字符串匹配的模式。索引中有一项权重

2对于问题，提供全文检索的fields为title+content，索引中还有一项问题权重，考虑"回答数"+"关注人数"，算出权重，
检索匹配的是使用 “标题” + “内容描述”的方式，title的权重是2，内容描述权重是1，

3对于回答，提供全文检索的fields为content，并且索引中有一项权重，按照该回答的用户的“粉丝数”“赞同数”“感谢数”，算出权重


4对于话题，提供全文检索的fields为话题描述，索引中还有一项是权重数，参考关注人数加权。


排序：
1索引中的权重是不可以被索引到的，旨在用来对文档排序是进行加权W1

2对于回答，少于140个字的答案会被惩罚W2

3使用的排序算法除了1，2，其余的默认得分W3，可参考https://lucene.apache.org/core/4_7_1/core/org/apache/lucene/search/similarities/TFIDFSimilarity.html

4最终的得分W=W1*W2*W3

