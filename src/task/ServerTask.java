package task;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;

import model.Account;
import model.BigCategory;
import model.Category;
import model.CollectedInfo;
import model.Product;
import model.Tuple;
import network.Direction;
import network.EventType;
import network.Protocol;
import network.ProtocolType;
import network.Response;
import network.ResponseType;
import utility.IOHandler;

public class ServerTask implements Runnable{
	
	private final Socket clientSocket;
	
	public ServerTask(Socket clientSocket) {
		this.clientSocket = clientSocket;
	}

	@Override
	public void run() {
		IOHandler.getInstance().log("[담당일찐 스레드] 스레드 생성 완료");
		
		try {
			ObjectInputStream inputStream = new ObjectInputStream(clientSocket.getInputStream());
			
			// 일단 프로토콜로 해석
			Protocol receivedProtocol = (Protocol) inputStream.readObject();
			
			// 프로토콜의 타입이 무엇인가?
			switch(receivedProtocol.getType()){
				case LOGIN:
					onLogin(receivedProtocol);
					break;
				case REGISTER:
					onRegister(receivedProtocol);
					break;
				case EVENT:
					onEvent(receivedProtocol);
					break;
				case ERROR:
					break;
				default :
					break;
			}
			
			clientSocket.close();
		} 
		catch (IOException e) {
			e.printStackTrace();
		}
		catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		
		// 소켓 다시한번 종료
		if(clientSocket != null) {
			try {
				clientSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		IOHandler.getInstance().log("[담당일찐 스레드] 스레드 종료됨");
	}
	
	private void sendOutputStream(Protocol protocol) throws Exception {
		ObjectOutputStream objectOutputStream = new ObjectOutputStream(clientSocket.getOutputStream());
		objectOutputStream.writeObject(protocol);
		objectOutputStream.flush();
	}
	
	private void onLogin(Protocol receivedProtocol) throws Exception{
		// 로그인이면 계정 작업 생성
		AccountTask at = new AccountTask();
		
		// 사용자에게서 받아온 계정 정보 획득 후 로그인 시도
		Account account = (Account) receivedProtocol.getObject();
		Response response = at.tryLogin(account);
		Protocol sendProtocol = new Protocol(ProtocolType.LOGIN, Direction.TO_CLIENT, response, null);
		
		// 결과를 전송함.
		sendOutputStream(sendProtocol);
	}
	
	private void onRegister(Protocol receivedProtocol) throws Exception{
		// 회원가입 계정 작업 생성
		AccountTask at = new AccountTask();
		
		// 사용자에게서 받아온 계정 정보 획득 후 회원가입 시도
		Account account = (Account) receivedProtocol.getObject();
		Response response = at.register(account);
		Protocol sendProtocol = new Protocol(ProtocolType.REGISTER, Direction.TO_CLIENT, response, null);
		
		// 결과를 전송함.
		sendOutputStream(sendProtocol);
	}
	
	// 이벤트는 경우의 수가 많기 때문에 한번 더 getEventType으로 케이스 분기함.
	// 이벤트 관련 Task들은 리턴값으로 Tuple을 받아, Response와 Object를 같이 받을 수 있게 함.
	private void onEvent(Protocol receivedProtocol) throws Exception{
		switch(receivedProtocol.getEventType()) {
			case GET_BIG_CATEGORY:
				onGetBigCategory(receivedProtocol);
				break;
			case GET_CATEGORY:
				onGetCategory(receivedProtocol);
				break;
			case SEARCH:
				onSearch(receivedProtocol);
				IOHandler.getInstance().log("[담당일찐 스레드] 검색 결과 반환 완료");
				break;
			case GET_PRODUCT_DETAIL:
				onGetProductDetail(receivedProtocol);
				IOHandler.getInstance().log("[담당일찐 스레드] 상세 정보 반환 완료");
				break;
			default:
				break;
		}
	}
	
	private void onSearch(Protocol receivedProtocol) throws Exception {
		// 상품 작업 생성
		ProductTask pt = new ProductTask();
		
		// 사용자에게서 받아온 검색어 획득 후 검색
		String searchWord = (String) receivedProtocol.getObject();
		Tuple<Response, ArrayList<Product>> productResult = pt.searchByProductName(searchWord);
		
		// 상품정보 응답 및 검색결과 받아옴
		Response response = productResult.getFirst();
		ArrayList<Product> productList = productResult.getSecond();
		
		// 상품정보 - 최근가격정보가 쌍으로 이루어진 결과 배열 생성
		ArrayList<Tuple<Product, CollectedInfo>> totalResult = new ArrayList<Tuple<Product,CollectedInfo>>();
		
		// 상품정보가 존재한다면 최근 가격도 가져온다.
		if(productList != null && productList.size() > 0) {
			CollectedInfoTask cit = new CollectedInfoTask();
			// 상품정보 하나씩, 최근 가격 가져옴
			for(Product p : productList) {
				Tuple<Response, ArrayList<CollectedInfo>> collectedInfoResult = cit.findByProduct(p);
				ArrayList<CollectedInfo> collectedInfoList = collectedInfoResult.getSecond();
				
				// 최근 가격 가져와서 최종 결과 배열에 추가.
				if(collectedInfoList != null && collectedInfoList.size() > 0) {
					CollectedInfo ci = collectedInfoList.get(0);
					totalResult.add(new Tuple<Product, CollectedInfo>(p, ci));
				}
				
			}
		}
		
		// 상품정보+최신수집정보를 포함시킨 프로토콜 생성
		Protocol sendProtocol = new Protocol(ProtocolType.EVENT, Direction.TO_CLIENT, EventType.SEARCH, response, (Object)totalResult);
		
		// 결과를 전송함.
		sendOutputStream(sendProtocol);
	}
	
	private void onGetProductDetail(Protocol receivedProtocol) throws Exception{
		// 수집 정보 작업 생성
		CollectedInfoTask cit = new CollectedInfoTask();
		
		// 사용자에게서 받아온 상품정보 획득 후 검색
		Product product = (Product) receivedProtocol.getObject();
		Tuple<Response, ArrayList<CollectedInfo>> result = cit.findByProduct(product);
		
		// 응답 및 검색결과 받아옴
		Response response = result.getFirst();
		ArrayList<CollectedInfo> collectedInfoList = result.getSecond();
		
		// 수집 정보를 포함시킨 프로토콜 생성
		Protocol sendProtocol = new Protocol(ProtocolType.EVENT, Direction.TO_CLIENT, EventType.GET_PRODUCT_DETAIL, response, (Object)collectedInfoList); 
		
		// 결과를 전송함.
		sendOutputStream(sendProtocol);
	}
	
	private void onGetBigCategory(Protocol receivedProtocol) throws Exception{
		// 대분류 작업 생성
		BigCategoryTask bct = new BigCategoryTask();
		
		// 대분류 다 가져옴
		Tuple<Response, ArrayList<BigCategory>> result = bct.getAllBigCategory();
		
		// 응답과, 대분류 분리
		Response response = result.getFirst();
		ArrayList<BigCategory> bigCategoryList = result.getSecond();
		
		// 대분류 목록을 포함시킨 프로토콜 생성
		Protocol sendProtocol = new Protocol(ProtocolType.EVENT, Direction.TO_CLIENT, EventType.GET_BIG_CATEGORY, response, (Object)bigCategoryList); 
		
		// 결과를 전송함.
		sendOutputStream(sendProtocol);
	}
	
	private void onGetCategory(Protocol receivedProtocol) throws Exception{
		// 분류 작업 생성
		CategoryTask ct = new CategoryTask();
		
		// 사용자에게서 대분류 받음. 그걸로 검색
		BigCategory bigCategory = (BigCategory) receivedProtocol.getObject();
		Tuple<Response, ArrayList<Category>> result = ct.findByBigCategory(bigCategory);
		
		// 응답과, 분류 분리
		Response response = result.getFirst();
		ArrayList<Category> categoryList = result.getSecond();
		
		// 분류 목록 포함시킨 프로토콜 생성
		Protocol sendProtocol = new Protocol(ProtocolType.EVENT, Direction.TO_CLIENT, EventType.GET_CATEGORY, response, (Object)categoryList); 
		
		// 결과를 전송함.
		sendOutputStream(sendProtocol);
	}

}
