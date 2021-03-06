package com.dubboclub.dk.task;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.alibaba.dubbo.common.logger.Logger;
import com.alibaba.dubbo.common.logger.LoggerFactory;
import com.alibaba.dubbo.common.utils.ConfigUtils;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.dubboclub.dk.storage.DayTradingStorage;
import com.dubboclub.dk.storage.TradingStatisticStorage;
import com.dubboclub.dk.storage.model.DayTradingPo;
import com.dubboclub.dk.storage.model.StatisticObject;
import com.dubboclub.dk.storage.model.TradingStatisticPo;
import com.dubboclub.dk.web.utils.ConstantsUtil;

@Component
public class TradingStatisticTaskImpl implements TradingStatisticTask {
	private static final Logger logger = LoggerFactory.getLogger(TradingStatisticTaskImpl.class);
	@Autowired
	@Qualifier("tradingStatisticStorage")
	private TradingStatisticStorage tradingStatisticStorage;
	@Autowired
	@Qualifier("dayTradingStorage")
	private DayTradingStorage dayTradingStorage;
	private final static String BIZ_EXCEPTION_URL="/zipkin/api/v2/traces?";
	long lastEndTime = 0;
	private String zipkinUrl;
	@PostConstruct
    public void init() {
    	zipkinUrl = ConfigUtils.getProperty("zipkin.url");
    }
	@Scheduled(cron="0/10 * *  * * ? ")   //每10秒执行一次    
	@Override 
    public void getTradingStatisticTask(){
		Long startT;
		Long endT;
		if(lastEndTime == 0){
			lastEndTime = new Date().getTime() -10000;
			startT = lastEndTime - 10000;
			endT = lastEndTime ;
		} else {
			startT = lastEndTime + 1;
			endT = lastEndTime + 10000;
			lastEndTime = endT;
		}
		RestTemplate restTemplate = new RestTemplate();         
		String data=null;
		try {
			data = restTemplate.getForObject(zipkinUrl + BIZ_EXCEPTION_URL + "&endTs=" + endT + "&lookback=10000"  , String.class);
			
		} catch (Exception e) {
			logger.warn(zipkinUrl + BIZ_EXCEPTION_URL + "&startTs=" + startT + "&endTs=" + endT);
			return;
		}
		
		JSONArray jsonTrads = JSONArray.parseArray(data);
		if(jsonTrads == null || jsonTrads.size() == 0){
			DayTradingPo dayTradingPo = new DayTradingPo();
			dayTradingPo.setTxCode("Reserved");
			dayTradingPo.setTotalTimeNum(0);
			dayTradingPo.setStartTime(new SimpleDateFormat(ConstantsUtil.DATE_FORMATD).format(new Date()));
			dayTradingPo.setTimestamp(((new Date()).getTime())/1000);
			dayTradingStorage.addDayTrading(dayTradingPo);
		}
		Map<String, StatisticObject> statisticMap = new HashMap<String, StatisticObject>();  //交易量，平均耗时，成功或失败次数Map
//		Map<String, DayStatisticObject> txCodeMap = new HashMap<String, DayStatisticObject>();  //每日峰值内层Map
//		Map<String, Map<String, DayStatisticObject>> dayTradingMap = new HashMap<String, Map<String, DayStatisticObject>>();  ////每日峰值外层Map
		
		
		for(Object jsonTrad : jsonTrads ){
			String txCode = "";
			long duration = 0;
			boolean success = false;
			String nowTime = "";
			String startTime = "";
			long timestamp1 = 0;
			if(jsonTrad instanceof JSONArray){
					for(Object text : (JSONArray)jsonTrad){
						txCode = ((JSONObject) text).getJSONObject("tags").getString("txCode");
						String kind = ((JSONObject)text).getString("kind");
						String error = ((JSONObject) text).getJSONObject("tags").getString("error");
						if(txCode != null || txCode !="" && text instanceof JSONObject ){
							long timestamp = ((JSONObject)text).getLong("timestamp");
							timestamp1 = Integer.parseInt(timestamp/10000000+"0");
							nowTime = new SimpleDateFormat(ConstantsUtil.DATE_FORMATE).format(new Date(timestamp/1000));
							startTime = new SimpleDateFormat(ConstantsUtil.DATE_FORMAT).format(new Date().getTime());
							if(kind.equalsIgnoreCase("SERVER") && error == null){
								success = true;
							}else if(kind.equalsIgnoreCase("CLIENT") ){
								duration = ((JSONObject)text).getLong("duration");
	
							};
							
						};
					};
					
					
				}
			
			
			//交易量，平均耗时，成功或失败次数Map
			StatisticObject	object = statisticMap.get(txCode);
			if(object == null){
				object = new StatisticObject();
				object.setTotalNum(1);
				object.setTotalTimePerTime(duration);
				object.setNowTime(nowTime);
				object.setTimestamp(timestamp1);
				object.setStartTime(startTime);
				if(success)
					object.setSuccess(1);
				else
					object.setFail(1);
				statisticMap.put(txCode, object);
				}					
			else {
				object.setTotalNum(object.getTotalNum()+1); 
				object.setTotalTimePerTime(object.getTotalTimePerTime()+duration);
				if(success)
					object.setSuccess(object.getSuccess()+1);
				else
					object.setFail(object.getFail()+1);
				statisticMap.put(txCode, object);
				
				}
			object.setTotalDayTimePer(object.getTotalDayTimePer()+1);
			statisticMap.put(txCode, object);
			}
		
		
		//遍历交易量，平均耗时，成功或失败次数Map
		for(String key : statisticMap.keySet())
        {
			
			StatisticObject value = statisticMap.get(key);
			TradingStatisticPo tradingStatisticPo = new TradingStatisticPo();
			tradingStatisticPo.setTxCode(key);
			tradingStatisticPo.setNowTime(value.getNowTime());
			TradingStatisticPo dataPo = tradingStatisticStorage.selectTradingStatisticByTxCode(tradingStatisticPo);
			
			
			if(dataPo == null){
				BeanUtils.copyProperties(value, tradingStatisticPo);
				tradingStatisticPo.setTimeAvg(value.getTotalTimePerTime()/value.getTotalNum());
				tradingStatisticPo.setTxCode(key);
				tradingStatisticStorage.addTradingStatistic(tradingStatisticPo);
				
			}else{
				tradingStatisticPo.setTotalNum(dataPo.getTotalNum()+value.getTotalNum());
				tradingStatisticPo.setFail(dataPo.getFail()+value.getFail());
				tradingStatisticPo.setSuccess(dataPo.getSuccess()+value.getSuccess());				
				tradingStatisticPo.setTimeAvg(TimeAvg(key,value.getTotalNum(),value.getTotalTimePerTime(),dataPo.getTotalNum(),dataPo.getTimeAvg()));
				tradingStatisticStorage.updateTradingStatisticByTxCode(tradingStatisticPo);
			}
			//每日峰值
			DayTradingPo dayTradingPo = new DayTradingPo();
			dayTradingPo.setTxCode(key);
			dayTradingPo.setTotalTimeNum(value.getTotalDayTimePer());
			dayTradingPo.setStartTime(value.getStartTime());
			dayTradingPo.setTimestamp(value.getTimestamp());
			dayTradingStorage.addDayTrading(dayTradingPo);
			
		
        }
		
		
		
		
		
//		//每日峰值内层Map
//		DayStatisticObject objectCode = txCodeMap.get(txCode);
//		if(objectCode == null){
//			objectCode = new DayStatisticObject();
//			objectCode.setTotalTimeNum(1);
//			txCodeMap.put(txCode, objectCode);
//		}else{
//			objectCode.setTotalTimeNum(objectCode.getTotalTimeNum()+1);
//			txCodeMap.put(txCode, objectCode);
//		}
//		//每日峰值外层Map
//		dayTradingMap.put(nowTime, txCodeMap);
		
		
		
//		//遍历每日峰值Map
//		for(String keys : dayTradingMap.keySet())
//		{
//			Map<String, DayStatisticObject> values = dayTradingMap.get(keys);
//			DayTradingPo dayTradingPo = new DayTradingPo();
//			dayTradingPo.setStartTime(keys);
//			
//			for(String key : txCodeMap.keySet()){
//				DayStatisticObject value = txCodeMap.get(key);
//				dayTradingPo.setTxCode(key);
//				dayTradingPo.setStartTime(value.getStartTime());
//				DayTradingPo dataDayPo = dayTradingStorage.selectDayTradingByTxCode(dayTradingPo);
//				if(dataDayPo == null){
//					BeanUtils.copyProperties(value, dayTradingPo);
//					dayTradingPo.setTxCode(key);
//					dayTradingStorage.addDayTrading(dayTradingPo);
//				}else{
//					dayTradingPo.setTotalTimeNum(dataDayPo.getTotalTimeNum()+value.getTotalTimeNum());
//					dayTradingStorage.updateDayTradingByTxCode(dayTradingPo);
//				}
//				
//			}
//			
//		}
		

	

	}
	//遍历每日峰值Map
//	private static void entrySet(Map<String, Map<String, DayStatisticObject>> dayTradingMap) {
//	      Set<Map.Entry<String, Map<String, DayStatisticObject>>> entry =  dayTradingMap.entrySet();
//	      Iterator<Map.Entry<String, Map<String, DayStatisticObject>>>  it = entry.iterator();
//	      while (it.hasNext()){
//	          Map.Entry<String, Map<String, DayStatisticObject>> day = it.next();
//	          String startTime = day.getKey();
//	          Map<String, DayStatisticObject> map = day.getValue();
//	          Set<Map.Entry<String ,DayStatisticObject >> entryTxCode = map.entrySet();
//	          Iterator<Map.Entry<String,DayStatisticObject >> itTxCode = entryTxCode.iterator();
//	          while (itTxCode.hasNext()){
//	              Map.Entry<String ,DayStatisticObject > byTxCode = itTxCode.next();
//	              String txCode = byTxCode.getKey();
//	              DayStatisticObject totalTimeNum  = byTxCode.getValue();
//	          }                                                                                                                                                     
//	      }                                                                                                                                                         
//	}                                                                                
	//求平均耗时
	private double TimeAvg(String txCode,int totalNum,long totalTimePerTime,int oldTotal,double oleTimeAvg){
		double totalNum1 = oldTotal+totalNum;
		double duration1 = oleTimeAvg*oldTotal+totalTimePerTime;
		double avg = (duration1/totalNum1);
		return avg;
	}
	
}

