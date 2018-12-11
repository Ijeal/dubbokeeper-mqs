/**
 * 
 */
package com.dubboclub.dk.storage.mysql.mapper;

import org.apache.ibatis.annotations.Param;

import com.dubboclub.dk.storage.model.BizWarningPo;

/**
 * Copyright: Copyright (c) 2018 东华软件股份公司
 * 
 * @Description: 该类的功能描述
 *
 * @author: 黄祖真
 * @date: 2018年12月7日 上午10:28:35 
 *
 */
public interface BizWarningMapper {
	public BizWarningPo selectBizWarningById(@Param("bizWarning")BizWarningPo bizWarning);
	public Integer deleteBizWarningById(@Param("bizWarning")BizWarningPo bizWarningPo);
}
