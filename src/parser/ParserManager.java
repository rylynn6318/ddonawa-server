package parser;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import model.CollectedInfo;
import model.Product;
import model.Tuple;
import utility.IOHandler;

// 파싱용 쓰레드 생성 및 관리
public class ParserManager {
	
	// 싱글톤 패턴
	private static ParserManager _instance;
	public static ParserManager getInstance() {
		if(_instance == null)
			_instance = new ParserManager();
		return _instance;
	}
	private final static int CORE_POOL_SIZE = 2;		// 기본 셀레니움 개수 (서버 실행 시 생성)
	private final static int MAX_POOL_SIZE = 12;			// 최대 셀레니움 개수 (추가 생성. 느리다)
	
	private final ThreadPoolExecutor threadPoolExecutor;
	private ArrayList<SeleniumManager> seleniumManagerList = new ArrayList<SeleniumManager>();		// 셀레니움 종료를 위해 리스트 가지고있음.
	
	// 셀레니움 매니저 미리 로드
	public ParserManager() {
		// 커스텀 쓰레드 만들어서 셀레니움 매니저 박아넣음.
		threadPoolExecutor = new ThreadPoolExecutor(CORE_POOL_SIZE, MAX_POOL_SIZE, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), new ThreadFactory (){
			public Thread newThread(Runnable r) {
				SeleniumManager sm = new SeleniumManager();
				seleniumManagerList.add(sm);
				CustomThread ct = new CustomThread(r, sm);
				ct.setDaemon(true);
				return ct;
			}});
		threadPoolExecutor.prestartAllCoreThreads();
		
	}
	
	// 다나와와 네이버쇼핑에서 상품찾기 파싱 요청을 한다.
	public Tuple<ArrayList<CollectedInfo>, ArrayList<CollectedInfo>> requestParse(Product product) {
		Future<ArrayList<CollectedInfo>> danawaResult = null, naverShopResult = null;
		
		try {
			ParserTask danawaTask = new ParserTask(product, new DanawaParser());
			danawaResult = threadPoolExecutor.submit(danawaTask);
			
			ParserTask naverShopThread = new ParserTask(product, new NaverShopParser());
			naverShopResult = threadPoolExecutor.submit(naverShopThread);
		}
		catch(Exception e) {
			IOHandler.getInstance().log("ParserManager.requestParse-submit", e);
		}
		
		try {
			ArrayList<CollectedInfo> danawaParsed = danawaResult.get();
			ArrayList<CollectedInfo> naverShopParsed = naverShopResult.get();
			if(danawaParsed != null && naverShopParsed != null) {
				return new Tuple<ArrayList<CollectedInfo>, ArrayList<CollectedInfo>>(danawaParsed, naverShopParsed);
			}
			else if (danawaParsed != null){
				return new Tuple<ArrayList<CollectedInfo>, ArrayList<CollectedInfo>>(danawaParsed, null);
			}
			else {
				return new Tuple<ArrayList<CollectedInfo>, ArrayList<CollectedInfo>>(null, naverShopParsed);
			}
		}
		catch(Exception e) {
			IOHandler.getInstance().log("ParserManager.requestParse-get", e);
		}
		return null;
	}
	
	public void close() {
		// 셀레니움 매니저 종료
		for(SeleniumManager sm : seleniumManagerList) {
			if(sm.isDriverAlive()) {
				sm.quit();
			}
		}

		if(!threadPoolExecutor.isShutdown() || !threadPoolExecutor.isTerminated()) {
			threadPoolExecutor.shutdown();
		}
	}

}
