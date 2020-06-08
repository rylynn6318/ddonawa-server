package task;

import java.util.ArrayList;

import db.ProductManager;
import model.Product;
import model.Tuple;
import network.Response;
import network.ResponseType;
import utility.IOHandler;

public class ProductTask {
	
	// 사용자가 검색했을 때 동작하는 메소드. 검색하서 결과 상품 목록을 클라이언트에 반환함.
	public Tuple<Response, ArrayList<Product>> searchByProductName(String searchWord) {
		try {
			// 문자열 정규화 등 선처리. 현재는 별도의 처리 없이 그대로 DB에 SELECT함.
			
			// SQL에 검색
			ProductManager pm = new ProductManager();
			ArrayList<Product> searchResult = pm.searchByProductName(searchWord);
			
			// 결과 반환
			Response response = new Response(ResponseType.SUCCEED, "검색 성공");
			return new Tuple<Response, ArrayList<Product>>(response, searchResult);
		}
		catch(Exception e) {
			IOHandler.getInstance().log("ProductTask.search", e);
		}
		return null;
	}
	
	// 사용자가 상품정보를 열람했을 때 동작하는 메소드.
	public boolean view() {
		return false;
	}
	
	
}
