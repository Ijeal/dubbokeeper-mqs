package com.dubboclub.dk.storage;

import java.util.List;

import com.dubboclub.dk.storage.model.DayTradingPo;
import com.dubboclub.dk.storage.model.DayTradingQuery;

public interface DayTradingStorage {
	public Integer addDayTrading(DayTradingPo dayTradingPo);
	public Integer updateDayTradingByTxCode(DayTradingPo dayTradingPo);
	public Integer deleteDayTradingByPageByCondition(DayTradingPo dayTradingPo);
	public DayTradingPo selectDayTradingByTxCode(DayTradingPo dayTradingPo);
	public List<DayTradingPo> selectDayTradingByPageByCondition(DayTradingQuery dayTradingQuery);
}
