package edu.unh.cs980.WordEmbedding;
import org.apache.lucene.search.Query;

/*****
 * This class is useful for entity as Query
 *
 */


public class AnnotationQuery {
	private final Method method1, method2;
	
	AnnotationQuery(Method method1, Method method2) {
		this.method1 = method1;
		this.method2 = method2;
	}
	
	public <T extends Annotation> T getAnnotation(Class<T> type) {
		T back = method1.getAnnotation(type);
		if (back == null && method2 != null) {
			back = method2.getAnnotation(type);
		}
		
		return back;
	}
	
	public Annotation[] getAnnotations() {
		List<Annotation> back = new ArrayList<Annotation>();
		
		back.addAll(Arrays.asList(method1.getAnnotations()));
		if (method2 != null) {
			back.addAll(Arrays.asList(method2.getAnnotations()));
		}
		
		return back.toArray(new Annotation[back.size()]);
	}

	public Method getMethod1() {
		return method1;
	}

	public Method getMethod2() {
		return method2;
	}
}
class EntityQueryObj {
	String queryStr;
	Query query;

	public void setQueryStr(String str) {
		this.queryStr = str;
	}

	public String getQueryStr() {
		return this.queryStr;
	}

	public void setQuery(Query q) {
		this.query = q;
	}

	public Query getQuery() {
		return this.query;
	}
}

