package com.landray.kmss.third.mail.alimail.service.spring;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.transaction.TransactionStatus;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.landray.kmss.common.dao.HQLInfo;
import com.landray.kmss.sys.appconfig.model.BaseAppConfig;
import com.landray.kmss.sys.appconfig.model.SysAppConfig;
import com.landray.kmss.sys.appconfig.service.ISysAppConfigService;
import com.landray.kmss.sys.language.utils.SysLangUtil;
import com.landray.kmss.sys.organization.model.SysOrgElement;
import com.landray.kmss.sys.organization.model.SysOrgPerson;
import com.landray.kmss.sys.organization.service.ISysOrgElementService;
import com.landray.kmss.sys.organization.service.ISysOrgPersonService;
import com.landray.kmss.sys.organization.util.PasswordUtil;
import com.landray.kmss.third.mail.alimail.model.AlimailOrgMapping;
import com.landray.kmss.third.mail.alimail.model.AlimailSyncFailMapping;
import com.landray.kmss.third.mail.alimail.service.IAlimailOrgMappingService;
import com.landray.kmss.third.mail.alimail.service.IAlimailSyncFailMappingService;
import com.landray.kmss.third.mail.alimail.util.AliMailUtil;
import com.landray.kmss.third.mail.alimail.util.HttpClientUtil;
import com.landray.kmss.third.mail.alimail.util.SortByHierarchyLengthComparator;
import com.landray.kmss.util.DateUtil;
import com.landray.kmss.util.SpringBeanUtil;
import com.landray.kmss.util.StringUtil;
import com.landray.kmss.util.TransactionUtils;

/**
 * 阿里邮箱组织架构同步服务
 * @author 严海星
 * 2018年12月28日
 */
public class AliMailOrgSynchroService {
	
	private static final Log logger = LogFactory.getLog(AliMailOrgSynchroService.class);
	
	//全量范围
	private static final Integer RANGE_ALL = 1;
	//增量范围
	private static final Integer RANGE_NEW = 2;
	
	//同步错误映射记录所使用的type
	private static final String CREATE_FAIL_ORG_TYPE = "1"; //创建部门失败类型
	private static final String CREATE_FAIL_USER_TYPE = "2";//创建用户失败类型
	
	public void test() throws Exception
	{
		//System.out.println(getLastSynchroTime());
		//getSynchroNewUser();
		//executeSynchro();
		//System.out.println(getLastSynchroTime());
		/*IAlimailOrgMappingService alimailOrgMappingService = (IAlimailOrgMappingService) SpringBeanUtil.getBean("alimailOrgMappingService");
		HQLInfo selectUrlHql = new HQLInfo();
		//selectUrlHql.setFromBlock("AlimailOrgMapping");
		selectUrlHql.setSelectBlock("alimailOrgMapping");
		List<AlimailOrgMapping> mappingList = alimailOrgMappingService.findList(selectUrlHql);
		for(AlimailOrgMapping a : mappingList){
			System.out.println(a.getFdId());
		}*/
		/*System.out.println(com.landray.kmss.util.DateUtil.getCalendar(new Date()));
		//stem.out.println(com.landray.kmss.util.DateUtil.convertDateToString(new Date(), "yyyy-MM-dd HH:mm:ss"));
		//System.out.println(DateUtil.formatDate(new Date(), "yyyy-MM-dd hh:mm:ss"));*/
	}
	
	/**
	 * 获取配置信息根据key
	 * @param key
	 * @return
	 * @throws Exception
	 * @author 严海星
	 * 2019年4月26日
	 */
	@SuppressWarnings("unchecked")
	private String getConfigValue(String key) throws Exception
	{
		ISysAppConfigService appConfigService = (ISysAppConfigService) SpringBeanUtil.getBean("sysAppConfigService");
		HQLInfo selectUrlHql = new HQLInfo();
		selectUrlHql.setSelectBlock("sysAppConfig");
		selectUrlHql.setWhereBlock("fdField = '"+key+"'");
		List<SysAppConfig> list = appConfigService.findList(selectUrlHql);
		if(list.size() > 0){
			return list.get(0).getFdValue();
		}else{
			return null;
		}
	}
	
	/**
	 * 获取最近一次执行同步的时间
	 * @return
	 * @author 严海星
	 * 2018年12月28日
	 * @throws Exception 
	 */
	@SuppressWarnings("unchecked")
	private String getLastSynchroTime() throws Exception
	{
		//String lastTime = new AliMailConfig().getValue("kmss.alimail.org.sync.last.time");
		ISysAppConfigService appConfigService = (ISysAppConfigService) SpringBeanUtil.getBean("sysAppConfigService");
		HQLInfo selectUrlHql = new HQLInfo();
		selectUrlHql.setSelectBlock("sysAppConfig");
		selectUrlHql.setWhereBlock("fdField = 'kmss.alimail.org.sync.last.time'");
		List<SysAppConfig> list = appConfigService.findList(selectUrlHql);
		if(list.size() > 0){
			return list.get(0).getFdValue();
		}else{
			return null;
		}
	}
	
	/**
	 * 设置最后同步时间
	 * @param date
	 * @author 严海星
	 * 2018年12月29日
	 * @throws Exception 
	 */
	private void setLastSynchroTime(Date date) throws Exception
	{
		BaseAppConfig appConfig = (BaseAppConfig) Class.forName("com.landray.kmss.third.mail.alimail.AliMailConfig").newInstance();
		appConfig.getDataMap().put("kmss.alimail.org.sync.last.time", DateUtil.convertDateToString(date, "yyyy-MM-dd HH:mm:ss"));
		appConfig.save();
	}
	
	/**
	 * 执行重试失败映射
	 * @throws Exception
	 * @author 严海星
	 * 2019年4月28日
	 */
	@SuppressWarnings("unchecked")
	public void executeTryFailMapping() throws Exception{
		IAlimailSyncFailMappingService alimailSyncFailMappingService = (IAlimailSyncFailMappingService) SpringBeanUtil.getBean("alimailSyncFailMappingService");
		//获取需要重试的错误映射
		HQLInfo selectUrlHql = new HQLInfo();
		selectUrlHql.setSelectBlock("alimailSyncFailMapping");
		selectUrlHql.setWhereBlock("fdIsNeedTry=1");
		List<AlimailSyncFailMapping> mappingList = alimailSyncFailMappingService.findValue(selectUrlHql);
		logger.info("[阿里云邮](重试任务执行)获取同步创建失败总数量:"+mappingList.size());
		if(mappingList.size() > 0){
			//分类,分出人员错误映射
			List<AlimailSyncFailMapping> userFailMapping = new ArrayList<AlimailSyncFailMapping>();
			Iterator<AlimailSyncFailMapping> it = mappingList.iterator();
			while(it.hasNext()){
				AlimailSyncFailMapping failInfo = it.next();
				if(CREATE_FAIL_USER_TYPE.equals(failInfo.getFdType())){
					userFailMapping.add(failInfo);
					it.remove();
				}
			}
			HttpClientUtil hc = new HttpClientUtil();
			//获取服务的wsurl
			String wsUrl = AliMailUtil.getWsUrl();
			//获取鉴权token
			String token = hc.getToken(wsUrl, AliMailUtil.getCode(), AliMailUtil.getPwd());
			if(StringUtil.isNull(token)){
				token = hc.getToken(wsUrl, AliMailUtil.getCode(), AliMailUtil.getPwd());
			}
			String accessTarget = AliMailUtil.getDomainStr().substring(1);
			
			//重试创建部门
			if(mappingList.size() > 0){
				logger.info("[阿里云邮]需要重试的部门数量:"+mappingList.size());
				IAlimailOrgMappingService alimailOrgMappingService = (IAlimailOrgMappingService) SpringBeanUtil.getBean("alimailOrgMappingService");
				ISysOrgElementService sysOrgElementService = (ISysOrgElementService) SpringBeanUtil.getBean("sysOrgElementService");
				for(AlimailSyncFailMapping failInfo : mappingList){
					JSONObject resultJo = requestToAliServer(accessTarget,token, JSON.parseObject(failInfo.getFdRequestJson()), wsUrl+"/ud/createDepartment");
					if("100".equals(resultJo.getJSONObject("status").getString("statusCode"))){
						SysOrgElement org = (SysOrgElement) sysOrgElementService.findByPrimaryKey(failInfo.getFdEkpId());
						//创建成功后建立部门映射信息
						AlimailOrgMapping orgMapping = new AlimailOrgMapping();
						orgMapping.setFdId(org.getFdId());
						orgMapping.setFdHierarchyId(org.getFdHierarchyId());
						orgMapping.setFdAlimailId(resultJo.getJSONObject("data").getString("departmentId"));
						alimailOrgMappingService.add(orgMapping);
						//创建成功后删除该错误映射信息
						alimailSyncFailMappingService.delete(failInfo);
					}else{//如果创建失败就更新
						failInfo.setFdRequsetTime(DateUtil.convertDateToString(new Date(), "yyyy-MM-dd HH:mm:ss"));
						failInfo.setFdResponseJson(resultJo.toJSONString());
						//判断是否是也存在部门导致创建不了
						if("2963".equals(resultJo.getJSONObject("status").getString("statusCode"))){
							failInfo.setFdIsNeedTry("0");//不需要重试
						}else{
							failInfo.setFdIsNeedTry("1");
						}
						//创建失败后更新原来的错误映射信息
						alimailSyncFailMappingService.update(failInfo);
					}
				}
			}
			//重试创建用户
			if(userFailMapping.size() > 0){
				logger.info("[阿里云邮]需要重试的人员数量:"+userFailMapping.size());
				for(AlimailSyncFailMapping failInfo : userFailMapping){
					JSONObject resultJo = requestToAliServer(accessTarget,token, JSON.parseObject(failInfo.getFdRequestJson()), wsUrl+"/ud/createAccounts");
					if("100".equals(resultJo.getJSONObject("status").getString("statusCode"))){
						JSONArray fail = resultJo.getJSONObject("data").getJSONArray("fail");
						if(fail.size() > 0){//创建失败的账户列表
							failInfo.setFdResponseJson(resultJo.toJSONString());
							failInfo.setFdRequsetTime(DateUtil.convertDateToString(new Date(), "yyyy-MM-dd HH:mm:ss"));
							//创建失败后更新原来的错误映射信息
							alimailSyncFailMappingService.update(failInfo);
						}else{//创建成功 
							//删除错误映射信息
							alimailSyncFailMappingService.delete(failInfo);
						}
					}else if("1610".equals(resultJo.getJSONObject("status").getString("statusCode"))){
						//阿里企业邮件人员已达上限
						failInfo.setFdResponseJson(resultJo.toJSONString());
						failInfo.setFdRequsetTime(DateUtil.convertDateToString(new Date(), "yyyy-MM-dd HH:mm:ss"));
						failInfo.setFdIsNeedTry("0");
						//创建失败后更新原来的错误映射信息
						alimailSyncFailMappingService.update(failInfo);
					}else{
						failInfo.setFdResponseJson(resultJo.toJSONString());
						failInfo.setFdRequsetTime(DateUtil.convertDateToString(new Date(), "yyyy-MM-dd HH:mm:ss"));
						//判断该失败是否是已经存在导致无法创建
						if("1601".equals(resultJo.getJSONObject("status").getString("statusCode"))){
							failInfo.setFdIsNeedTry("0");//已经存在导致的失败不需要重试
						}else{
							failInfo.setFdIsNeedTry("1");
						}
						//创建失败后更新原来的错误映射信息
						alimailSyncFailMappingService.update(failInfo);
					}
				}
			}
			
		}
	}
	
	/**
	 * 执行同步操作
	 * 
	 * @author 严海星
	 * 2019年1月4日
	 * @throws Exception 
	 */
	public void executeSynchro() throws Exception
	{
		//判断是否开启同步功能且打开阿里集成
		//if(AliMailUtil.isEnableOrgSync())
		if(AliMailUtil.isEnableAlimail() && AliMailUtil.isEnableOrgSync())
		{
			HttpClientUtil hc = new HttpClientUtil();
			//获取服务的wsurl
			String wsUrl = AliMailUtil.getWsUrl();
			//获取鉴权token
			String token = hc.getToken(wsUrl, AliMailUtil.getCode(), AliMailUtil.getPwd());
			if(StringUtil.isNull(token)){
				token = hc.getToken(wsUrl, AliMailUtil.getCode(), AliMailUtil.getPwd());
			}
			
			if(StringUtil.isNotNull(token)){
				
				//获取最后一次同步时间
				String lastSyncTime = getLastSynchroTime();
				//保存ekp部门id和阿里部门id的map,用于同步用户信息时使用
				Map<String,String> aliDeptIdMap = new HashMap<String,String>();
				//获取阿里云邮配置   email是否来自EKP
				String emailIsOnEkp = getConfigValue("kmss.alimail.org.sync.emailIsOnEkp");
				String domainMail = AliMailUtil.getDomainStr();
				//获取accessTarget
				String accessTarget = domainMail.substring(1);
				
				if(StringUtil.isNotNull(lastSyncTime)){//增量同步
					
					//获取增量的同步部门
					List<SysOrgElement> addOrgList = getSynchroOrg(RANGE_NEW);
					//获取增量的同步用户
					List<SysOrgPerson> addUserList = getSynchroUser(RANGE_NEW,emailIsOnEkp,domainMail);
					//获取修改的同步部门
					List<SysOrgElement> updateOrgList = getSynchroUpdateOrg(lastSyncTime);
					//获取修改的同步用户
					List<SysOrgPerson> updateUserList = getSynchroUpdateUser(lastSyncTime,emailIsOnEkp,domainMail);
					
					//同步时间
					Date syncTime = new Date();
					logger.info("[阿里云邮]开始执行增量同步---EKP系统获取新增的部门数量:"+addOrgList.size()+",获取新增的用户数量:"+addUserList.size()+",获取更新的部门数量:"+updateOrgList.size()+",获取更新的用户数量:"+updateUserList.size());
					//获取部门映射信息
					getEkpOrgIdAliOrgIdMap(aliDeptIdMap);
					/**
					 * start   同步操作  
					 */
					//执行增量部门的同步操作
					if(addOrgList.size() > 0)
						exeSynchroAddOrg(wsUrl,accessTarget,token, addOrgList, aliDeptIdMap,RANGE_NEW);
					//执行增量用户的同步操作
					if(addUserList.size() > 0)
						exeSynchroAddUser(wsUrl,accessTarget,token,addUserList,aliDeptIdMap,null,emailIsOnEkp);
					//执行修改用户的同步操作	必须比执行修改部门同步操作之前执行,原因可能要清除某个部门,部门下可能有人在导致无法删除部门
					if(updateUserList.size() > 0)
						exeSynchroUpdateUser(wsUrl,accessTarget,token,updateUserList,aliDeptIdMap,emailIsOnEkp);
					//执行修改部门的同步操作
					if(updateOrgList.size() > 0)
						exeSynchroUpdateOrg(wsUrl,accessTarget,token, updateOrgList, aliDeptIdMap);
					
					/**
					 * end   同步操作  
					 */
					
					//设置同步时间
					setLastSynchroTime(syncTime);
					
				}else{//全量同步
					
					//获取全量同步部门
					List<SysOrgElement> orgList = getSynchroOrg(RANGE_ALL);
					//获取全量同步用户
					List<SysOrgPerson> userList = getSynchroUser(RANGE_ALL,emailIsOnEkp,domainMail);
					//同步时间
					Date syncTime = new Date();
					
					logger.info("[阿里云邮]开始执行全量同步---EKP系统获取需要同步的部门数量:"+orgList.size()+",获取需要同步的用户数量:"+userList.size());
					
					//获取组织架构映射,可能存在初始化操作,有映射的部门信息
					getEkpOrgIdAliOrgIdMap(aliDeptIdMap);
					
					Set<String> keySet = aliDeptIdMap.keySet();
					List<String> aliDeptIds = new ArrayList<String>();
					for(String ekpid : keySet){
						aliDeptIds.add(aliDeptIdMap.get(ekpid));
					}
					
					//执行同步操作
					if(orgList.size() > 0){
						String rootDeptId = exeSynchroAddOrg(wsUrl,accessTarget,token, orgList, aliDeptIdMap,RANGE_ALL);
						aliDeptIds.add(rootDeptId);
					}
					if(userList.size() > 0)
						exeSynchroAddUser(wsUrl,accessTarget,token,userList,aliDeptIdMap,aliDeptIds,emailIsOnEkp);
					
					//设置同步时间
					setLastSynchroTime(syncTime);
				}
			}else{
				logger.error("[阿里云邮]组织架构同步失败:无法获取鉴权token");
			}
			
		}
			
	}
	
	/**
	 * 执行更新的用户同步操作
	 * @param token
	 * @param updateUserList
	 * @param aliDeptIdMap
	 * @author 严海星
	 * 2019年1月9日
	 * @throws Exception 
	 */
	private void exeSynchroUpdateUser(String wsUrl ,String accessTarget ,String token, List<SysOrgPerson> updateUserList,
			Map<String, String> aliDeptIdMap ,String emailIsOnEkp) throws Exception {
		String mail = AliMailUtil.getDomainStr();
		//获取根部门id
		String rootId = this.getDomainInfo(wsUrl,accessTarget,"rootDepartmentId", token);
		//用来收集更新的用户状态
		JSONArray accounts = new JSONArray();
		//用来收集更新的用户信息
		Map<String,JSONObject> updateUserParam = new HashMap<String,JSONObject>();
		
		//没存在阿里的用户
		List<SysOrgPerson> notExistUser = new ArrayList<SysOrgPerson>();
		//已存在阿里的用户
		List<SysOrgPerson> isExistUser = new ArrayList<SysOrgPerson>();
		//判断该账户是否存在阿里中
		JSONArray emails = new JSONArray();
		Map<String,SysOrgPerson> mailUserMap = new HashMap<String,SysOrgPerson>();
		for(SysOrgPerson user : updateUserList)
		{
			String userMail;
			if("true".equals(emailIsOnEkp)){
				userMail = user.getFdEmail();
			}else{
				userMail = user.getFdLoginName()+mail;
			}
			emails.add(userMail);
			mailUserMap.put(userMail, user);
		}
		JSONArray fields = new JSONArray();
		fields.add("email");
		JSONObject requestParam = new JSONObject();
		requestParam.put("fields", fields);
		requestParam.put("emails", emails);
		JSONObject responeseJo = requestToAliServer(accessTarget, token, requestParam, wsUrl + "/ud/getAccountsInfo");
		if("100".equals(responeseJo.getJSONObject("status").getString("statusCode"))){
			JSONArray notExist = responeseJo.getJSONObject("data").getJSONArray("fail");
			JSONArray isExist = responeseJo.getJSONObject("data").getJSONArray("dataList");
			for(int i = 0; i < notExist.size(); i++){
				JSONObject info = notExist.getJSONObject(i);
				String email = info.getString("email");
				SysOrgPerson user = mailUserMap.get(email);
				if(user.getFdIsAvailable()){//不存在阿里的用户判断有效的执行创建用户操作
					notExistUser.add(user);
				}
			}
			for(int i = 0; i < isExist.size(); i++){
				JSONObject info = isExist.getJSONObject(i);
				String email = info.getString("email");
				isExistUser.add(mailUserMap.get(email));
			}
			
		}else{
			logger.error("[阿里云邮]同步更新用户操作失败,无法从阿里服务获取ekp要更新的用户信息!返回信息:"+responeseJo.toJSONString());
		}
		//不存在阿里的有效用户进行创建
		exeSynchroAddUser(wsUrl, accessTarget, token, notExistUser, aliDeptIdMap,null,emailIsOnEkp);
		
		for(SysOrgPerson user : isExistUser)
		{
			String userMail ;
			if("true".equals(emailIsOnEkp)){
				userMail = user.getFdEmail();
			}else{
				userMail = user.getFdLoginName()+mail;
			}
			JSONObject param = new JSONObject();
			JSONObject account = new JSONObject();
			if(user.getFdIsAvailable()){//有效的账户更新账户信息	
				//密码同步更新
				if(user.getFdLastChangePwd()!=null && StringUtil.isNotNull(user.getFdLastChangePwd().toString()))
				{
					if(user.getFdAlterTime().toString().equals(user.getFdLastChangePwd().toString())){
						updatePassword(wsUrl,PasswordUtil.desDecrypt(user.getFdInitPassword()),
								userMail,token,accessTarget);
						param.put("initPasswdChanged", "1");
					}
				}
				param.put("name", user.getFdName());
				if(StringUtil.isNotNull(user.getFdMobileNo()))
					param.put("mobilePhone", user.getFdMobileNo());
				if(StringUtil.isNotNull(user.getFdNo()))
					param.put("employeeNo", user.getFdNo());
				//param.put("passwd", PasswordUtil.desDecrypt(user.getFdInitPassword()));
				if(user.getFdParent() != null){
					if(!StringUtil.isNotNull(aliDeptIdMap.get(user.getFdParent().getFdId()))){
						//需要生成部门
						SysOrgElement fdParent = user.getFdParent();
						createAliDept(fdParent,wsUrl,accessTarget,token,aliDeptIdMap);
					}
					param.put("departmentId",aliDeptIdMap.get(user.getFdParent().getFdId()) );
				}else{
					param.put("departmentId",rootId);
				}
				//param.put("userRole", "1".equals(user.getFdUserType())?0:1);//用户角色 1：普通用户 0：管理员。默认是1
				account.put("status", "0");//解冻账户
			}else{//无效用户更改状态  需要把用户移动到根部门再冻结账户
				param.put("departmentId",rootId);
				account.put("status", "1");//冻结账户
			}
			
			updateUserParam.put(userMail, param);
			
			account.put("email", userMail);
			accounts.add(account);
			
		}
		//更新账户信息
		Set<String> mailSets = updateUserParam.keySet();
		int updateSuccessTimes = 0;
		for(String userMail : mailSets)
		{
			JSONObject param = updateUserParam.get(userMail);
			JSONObject resultJo = requestToAliServer(userMail, token, param, wsUrl + "/ud/updateAccountInfo");
			//System.out.println("更新用户"+resultJo);
			if(!("100".equals(resultJo.getJSONObject("status").getString("statusCode")))){
				logger.error("[阿里云邮]更新用户信息失败,失败账户:["+userMail+"]失败信息:"+resultJo.get("data").toString());
			}else{
				updateSuccessTimes++;
			}
		}
		logger.info("[阿里云邮]成功同步更新用户数量:"+updateSuccessTimes);
		
		//更新账户状态
		if(accounts.size() > 0){
			JSONObject param = new JSONObject();
			param.put("accounts", accounts);
			JSONObject resultJo = requestToAliServer(accessTarget, token, param, wsUrl + "/ud/updateAccountsStatus");
			if("100".equals(resultJo.getJSONObject("status").getString("statusCode"))){
				JSONArray fail = resultJo.getJSONObject("data").getJSONArray("fail");
				if(fail.size() > 0){//创建失败的账户列表
					logger.error("[阿里云邮]更新账户状态失败列表:"+fail.toJSONString());
				}
			}
		}
		
	}
	
	private void updatePassword(String ws,String passw,String mail,String token,String accessTarget){
		String serverUrl = ws+ "/ud/updateAccountsPassword";
		JSONObject param = new JSONObject();
		JSONObject account = new JSONObject();
		account.put("password", passw);
		account.put("email", mail);
		JSONArray accounts = new JSONArray();
		accounts.add(account);
		param.put("accounts", accounts);
		
		JSONObject requestJo = new JSONObject();
		JSONObject access = new JSONObject();
		access.put("accessToken", token);
		access.put("accessTarget", accessTarget);
		//access.put("accessTarget", "yanhx@mlandray.com");
		requestJo.put("access", access);
		requestJo.put("param", param);
		//System.out.println("请求json:"+requestJo.toJSONString());
		HttpClientUtil hc = new HttpClientUtil();
		hc.httpDoPost(serverUrl, requestJo.toJSONString());
		//System.out.println(param.toJSONString());
		//System.out.println("响应json:"+result);
		/*if(StringUtil.isNotNull(result)){
		}*/
	}

	/**
	 * 执行更新的部门同步操作
	 * @param token
	 * @param updateOrgList
	 * @param aliDeptIdMap
	 * @author 严海星
	 * 2019年1月9日
	 * @throws Exception 
	 */
	private void exeSynchroUpdateOrg(String wsUrl ,String accessTarget ,String token, List<SysOrgElement> updateOrgList,
			Map<String, String> aliDeptIdMap) throws Exception {
		IAlimailOrgMappingService alimailOrgMappingService = (IAlimailOrgMappingService) SpringBeanUtil.getBean("alimailOrgMappingService");
		//获取根部门id
		String rootId = this.getDomainInfo(wsUrl,accessTarget,"rootDepartmentId", token);
		//需要删除的部门,收集起来重新排序进行删除处理,必须先把最底级的先删除
		List<AlimailOrgMapping> needDeleteOrg = new ArrayList<AlimailOrgMapping>();
		//成功更新部门数量
		int updateSuccessTimes = 0 ;
		
		for(SysOrgElement org : updateOrgList)
		{
			AlimailOrgMapping mapping = (AlimailOrgMapping) alimailOrgMappingService.findByPrimaryKey(org.getFdId(),null,true);
			if(mapping != null){
				JSONObject param = new JSONObject();
				if(org.getFdIsAvailable()){//查看是否有效
					JSONObject extend = new JSONObject();
					extend.put("order", org.getFdOrder());
					param.put("extend", extend);
					if(org.getFdHierarchyId().equals(mapping.getFdHierarchyId())){//判断父级部门是否有变化,有则更改父部门信息
						//更新部门名称
						param.put("departmentId", mapping.getFdAlimailId());
						param.put("name", org.getFdName());
						requestToAliServer(accessTarget,token, param, wsUrl+"/ud/updateDepartment");
					}else{//移动部门的更新
						param.put("departmentId", mapping.getFdAlimailId());
						if(org.getFdParent() != null){
							param.put("parentId", aliDeptIdMap.get(org.getFdParent().getFdId()));
						}else{//没有父类部门说明已经移动到根部门
							param.put("parentId",rootId);
						}
						param.put("name", org.getFdName());
						JSONObject resultJo = requestToAliServer(accessTarget,token, param, wsUrl+"/ud/updateDepartment");
						if("100".equals(resultJo.getJSONObject("status").getString("statusCode"))){
							mapping.setFdHierarchyId(org.getFdHierarchyId());
							//更新映射关系信息
							//needUpdateMapping.add(mapping);
							updateSuccessTimes++;
							alimailOrgMappingService.update(mapping);
						}else{
							logger.error("[阿里云邮]部门更新失败,失败部门:["+org.getFdName()+"]失败信息:"+resultJo.get("data").toString());
						}
					}
					
				}else{//部门失效    删除部门
					needDeleteOrg.add(mapping);
				}
			}else{//如果不存在映射关系则要重新创建部门
				if(org.getFdIsAvailable())//且还要判断是否有效的部门,失效的部门不处理
					createAliDept(org,wsUrl,accessTarget,token,aliDeptIdMap);
			}
		}
		logger.info("[阿里云邮]同步成功更新部门数量:" + updateSuccessTimes);
		//排序,底级排再最前
		Collections.sort(needDeleteOrg,new SortByHierarchyLengthComparator());
		
		
		logger.info("[阿里云邮]同步更新需要删除无效部门数量:"+needDeleteOrg.size());
		int deleteSuccessTimes = 0 ;
		//删除操作
		for(AlimailOrgMapping org : needDeleteOrg)
		{
			JSONObject param = new JSONObject();
			param.put("departmentId", org.getFdAlimailId());
			JSONObject resultJo = requestToAliServer(accessTarget,token, param, wsUrl+"/ud/removeDepartment");
			if("100".equals(resultJo.getJSONObject("status").getString("statusCode"))){
				//needDeleteMappingId.add(org.getFdId());
				deleteSuccessTimes++;
				//成功删除后删除映射数据
				alimailOrgMappingService.delete(org.getFdId());
			}else{
				logger.error("[阿里云邮]删除部门失败,失败部门:["+org.getFdId()+"]失败信息:"+resultJo.get("data").toString());
			}
		}
		logger.info("[阿里云邮]同步更新成功删除无效部门数量:" + deleteSuccessTimes);
		
	}

	private void createAliDept(SysOrgElement o,String wsUrl ,String accessTarget ,String token,Map<String,String> aliDepIdMap) throws Exception{
		//获取根部门id
		String rootId = this.getDomainInfo(wsUrl,accessTarget,"rootDepartmentId", token);
		String parentId = null;
		if(o.getFdParent() == null){
			parentId = rootId;
		}else{
			if(!StringUtil.isNotNull(aliDepIdMap.get(o.getFdParent().getFdId()))){
				//需要生成部门
				createAliDept(o.getFdParent(),wsUrl,accessTarget,token,aliDepIdMap);
			}
			parentId = aliDepIdMap.get(o.getFdParent().getFdId());
		}
		JSONObject param = new JSONObject();
		param.put("parentId", parentId);
		param.put("name", o.getFdName());
		//排序
		JSONObject extend = new JSONObject();
		extend.put("order", o.getFdOrder());
		param.put("extend", extend);
		JSONObject resultJo = requestToAliServer(accessTarget,token, param, wsUrl+"/ud/createDepartment");
		if("100".equals(resultJo.getJSONObject("status").getString("statusCode"))){
			AlimailOrgMapping orgMapping = new AlimailOrgMapping();
			orgMapping.setFdId(o.getFdId());
			orgMapping.setFdHierarchyId(o.getFdHierarchyId());
			orgMapping.setFdAlimailId(resultJo.getJSONObject("data").getString("departmentId"));
			aliDepIdMap.put(o.getFdId(), orgMapping.getFdAlimailId());
			IAlimailOrgMappingService alimailOrgMappingService = (IAlimailOrgMappingService) SpringBeanUtil.getBean("alimailOrgMappingService");
			alimailOrgMappingService.add(orgMapping);
		}
	}
	
	/**
	 * 全量同步时,会先获取已在阿里存在的用户,更本地ekp用户进行匹配更新操作
	 * @param wsUrl
	 * @param accessTarget
	 * @param token
	 * @param updateUserList
	 * @param aliDeptIdMap
	 * @throws Exception
	 * @author 严海星
	 * 2019年4月23日
	 */
	private void updateInitUserToAli(String wsUrl ,String accessTarget ,String token, List<SysOrgPerson> updateUserList,
			Map<String, String> aliDeptIdMap) throws Exception {
		String mail = AliMailUtil.getDomainStr();
		//用来收集更新的用户信息
		Map<String,JSONObject> updateUserParam = new HashMap<String,JSONObject>();
		// 获取根部门id
		String rootId = this.getDomainInfo(wsUrl, accessTarget, "rootDepartmentId", token);
		// 用来收集更新的用户状态
		JSONArray accounts = new JSONArray();

		for (SysOrgPerson user : updateUserList) {
			String userMail = user.getFdLoginName() + mail;
			JSONObject param = new JSONObject();
			JSONObject account = new JSONObject();
			if (user.getFdIsAvailable()) {// 有效的账户更新账户信息
				param.put("name", user.getFdName());
				//更新同步ekp密码
				param.put("passwd", PasswordUtil.desDecrypt(user.getFdInitPassword()));
				if (StringUtil.isNotNull(user.getFdMobileNo()))
					param.put("mobilePhone", user.getFdMobileNo());
				if (StringUtil.isNotNull(user.getFdNo()))
					param.put("employeeNo", user.getFdNo());
				if (user.getFdParent() != null) {
					if (!StringUtil.isNotNull(aliDeptIdMap.get(user.getFdParent().getFdId()))) {
						// 需要生成部门
						SysOrgElement fdParent = user.getFdParent();
						createAliDept(fdParent, wsUrl, accessTarget, token, aliDeptIdMap);
					}
					param.put("departmentId", aliDeptIdMap.get(user.getFdParent().getFdId()));
				} else {
					param.put("departmentId", rootId);
				}
				//param.put("userRole", "1".equals(user.getFdUserType()) ? 0 : 1);// 用户角色
																				// 1：普通用户
																				// 0：管理员。默认是1
				account.put("status", "0");// 解冻账户
			} else {// 无效用户更改状态 需要把用户移动到根部门再冻结账户
				param.put("departmentId", rootId);
				account.put("status", "1");// 冻结账户
			}

			updateUserParam.put(userMail, param);

			account.put("email", userMail);
			accounts.add(account);

		}
		// 更新账户信息
		Set<String> mailSets = updateUserParam.keySet();
		for (String userMail : mailSets) {
			JSONObject param = updateUserParam.get(userMail);
			JSONObject resultJo = requestToAliServer(userMail, token, param, wsUrl + "/ud/updateAccountInfo");
			if (!("100".equals(resultJo.getJSONObject("status").getString("statusCode")))) {
				logger.error("[阿里云邮]更新用户信息失败,失败账户:[" + userMail + "]失败信息:" + resultJo.get("data").toString());
			}
		}

		// 更新账户状态
		if (accounts.size() > 0) {
			JSONObject param = new JSONObject();
			param.put("accounts", accounts);
			JSONObject resultJo = requestToAliServer(accessTarget, token, param, wsUrl + "/ud/updateAccountsStatus");
			if ("100".equals(resultJo.getJSONObject("status").getString("statusCode"))) {
				JSONArray fail = resultJo.getJSONObject("data").getJSONArray("fail");
				if (fail.size() > 0) {// 创建失败的账户列表
					logger.error("[阿里云邮]更新账户状态失败列表:" + fail.toJSONString());
				}
			}
		}
	}
	
	/**
	 * 执行新增用户的同步
	 * @param token
	 * @param userList
	 * @param aliDepIdMap
	 * @param aliDeptIds	用于全量同步时获取阿里邮下对已存在的部门查看是否有人员在
	 * @throws Exception
	 * @author 严海星
	 * 2019年1月9日
	 * @param aliDeptIds 
	 */
	private void exeSynchroAddUser(String wsUrl ,String accessTarget ,String token ,List<SysOrgPerson> userList,Map<String,String> aliDepIdMap, List<String> aliDeptIds ,String emailIsOnEkp) throws Exception
	{
		if(aliDeptIds != null){
			//获取阿里组织架构下所有用户
			List<String> loginNames = getAlimailAllUser(aliDeptIds, token ,accessTarget);
			List<SysOrgPerson> existInAliUser = new ArrayList<SysOrgPerson>();
			Iterator<SysOrgPerson> it = userList.iterator();
			while(it.hasNext()){
				SysOrgPerson user = it.next();
				if(loginNames.contains(user.getFdLoginName())){
					existInAliUser.add(user);
					it.remove();
				}
			}
			//阿里已存在的用户,对比ekp用户,进行信息更新操作
			if(existInAliUser.size() > 0){
				updateInitUserToAli(wsUrl,accessTarget,token,existInAliUser,aliDepIdMap);
				logger.info("[阿里云邮]全量同步获取已在阿里云邮存在账户:" + existInAliUser.size());
			}
			
		}
		
		//用来收集创建失败的组织架构信息
		List<AlimailSyncFailMapping> createFailMappingList = new ArrayList<AlimailSyncFailMapping>();
		
		for(SysOrgPerson user : userList)
		{
			JSONArray accounts = new JSONArray();
			JSONObject account = new JSONObject();
			account.put("name", user.getFdName());
			account.put("passwd", PasswordUtil.desDecrypt(user.getFdInitPassword()));
			account.put("initPasswdChanged", "1");//密码以ekp为准,第一次登陆不需要再修改密码
			if("true".equals(emailIsOnEkp)){
				account.put("email", user.getFdEmail());
			}else{
				account.put("email", user.getFdLoginName()+AliMailUtil.getDomainStr());
			}
			if(StringUtil.isNotNull(user.getFdMobileNo()))
				account.put("mobilePhone", user.getFdMobileNo());
			if(StringUtil.isNotNull(user.getFdNo()))
				account.put("employeeNo", user.getFdNo());
			if(user.getFdParent() != null){
				if(!StringUtil.isNotNull(aliDepIdMap.get(user.getFdParent().getFdId()))){
					//需要生成部门
					SysOrgElement fdParent = user.getFdParent();
					createAliDept(fdParent,wsUrl,accessTarget,token,aliDepIdMap);
				}
				account.put("departmentId",aliDepIdMap.get(user.getFdParent().getFdId()));
			}
			//account.put("userRole", "1".equals(user.getFdUserType())?0:1);				//用户角色 1：普通用户 0：管理员。默认是1
			if(!user.getFdIsAvailable())
				account.put("activeStatus", "1");			//账户状态  0：正常   1:冻结 。 默认是0
			/*
			account.put("displayName", user.);*/			//显示名。当其他账户收到该账户发送的邮件的时候，会默认显示displayName
			/*account.put("displayAlias", user.);			//用于设置有打底域的帐户的默认主帐号。
			account.put("replyAddress", user.);
			account.put("saveToSendFolder", user.);
			account.put("smtpSaveToSendFolder", user.);
			account.put("initPasswdChanged", user.);
			account.put("storageSize", user.);
			account.put("allowedLogin", user.);
			account.put("migrateStatus", user.);
			account.put("jobTitle", user.);
			account.put("jobLevel", user.);
			account.put("workPhone", user.);
			account.put("mobilePhone", user.);
			account.put("officeLocation", user.);
			account.put("workLocation", user.);
			account.put("owner", user.);
			account.put("employeeType", user.);
			account.put("nickName", user.);
			account.put("contactShare", user.);
			account.put("userClass", user.);
			account.put("maxMailGroupNum", user.);
			account.put("customFieldsWithCache", user.);*/
			accounts.add(account);
			JSONObject param = new JSONObject();
			param.put("accounts", accounts);
			
			JSONObject resultJo = requestToAliServer(accessTarget,token, param, wsUrl+"/ud/createAccounts");
			if("100".equals(resultJo.getJSONObject("status").getString("statusCode"))){
				JSONArray fail = resultJo.getJSONObject("data").getJSONArray("fail");
				if(fail.size() > 0){//创建失败的账户列表
					logger.error("[阿里云邮]创建失败的账户列表:"+fail.toJSONString());
					AlimailSyncFailMapping failInfo = new AlimailSyncFailMapping();
					failInfo.setFdEkpId(user.getFdId());
					failInfo.setFdType(CREATE_FAIL_USER_TYPE);
					failInfo.setFdRequestJson(param.toJSONString());
					failInfo.setFdResponseJson(resultJo.toJSONString());
					failInfo.setFdRequsetTime(DateUtil.convertDateToString(new Date(), "yyyy-MM-dd HH:mm:ss"));
					failInfo.setFdIsNeedTry("1");
					createFailMappingList.add(failInfo);
				}
				//JSONArray success = resultJo.getJSONObject("data").getJSONArray("success");
				//logger.info("[阿里云邮]成功同步新增用户数量:"+success.size());
			}else if("1610".equals(resultJo.getJSONObject("status").getString("statusCode"))){
				logger.error("[阿里云邮]创建用户失败:阿里企业邮件人员已达上限!未同步人员["+user.getFdLoginName()+"]");
				AlimailSyncFailMapping failInfo = new AlimailSyncFailMapping();
				failInfo.setFdEkpId(user.getFdId());
				failInfo.setFdType(CREATE_FAIL_USER_TYPE);
				failInfo.setFdRequestJson(param.toJSONString());
				failInfo.setFdResponseJson(resultJo.toJSONString());
				failInfo.setFdRequsetTime(DateUtil.convertDateToString(new Date(), "yyyy-MM-dd HH:mm:ss"));
				failInfo.setFdIsNeedTry("0");
				createFailMappingList.add(failInfo);
			}else{
				logger.error("[阿里云邮]创建用户失败,返回值:"+resultJo+"--请求json:"+param.toJSONString());
				AlimailSyncFailMapping failInfo = new AlimailSyncFailMapping();
				failInfo.setFdEkpId(user.getFdId());
				failInfo.setFdType(CREATE_FAIL_USER_TYPE);
				failInfo.setFdRequestJson(param.toJSONString());
				failInfo.setFdResponseJson(resultJo.toJSONString());
				failInfo.setFdRequsetTime(DateUtil.convertDateToString(new Date(), "yyyy-MM-dd HH:mm:ss"));
				//判断该失败是否是已经存在导致无法创建
				if("1601".equals(resultJo.getJSONObject("status").getString("statusCode"))){
					failInfo.setFdIsNeedTry("0");//已经存在的不需要重试
				}else{
					failInfo.setFdIsNeedTry("1");
				}
				createFailMappingList.add(failInfo);
			}
		}
		
		if(createFailMappingList.size()>0){
			logger.error("[阿里云邮]同步创建人员失败数量:"+createFailMappingList.size());
			batchSaveSyncFailMapping(createFailMappingList,500);
		}
		
	}
	
	@SuppressWarnings("unchecked")
	private List<SysOrgPerson> getSynchroUser(Integer range ,String emailIsOnEkp,String domainMail) throws Exception
	{
		ISysOrgPersonService sysOrgPersonService = (ISysOrgPersonService) SpringBeanUtil.getBean("sysOrgPersonService");
		HQLInfo selectUrlHql = new HQLInfo();
		String hql ;
		selectUrlHql.setSelectBlock("sysOrgPerson");
		if(RANGE_NEW == range){//增量条件
			hql = "fdCreateTime = fdAlterTime and fdIsAvailable = 1 and fdIsBusiness !=0 and fdAlterTime >= '"+getLastSynchroTime()+"'";
			//selectUrlHql.setWhereBlock("fdCreateTime = fdAlterTime and fdIsAvailable = 1 and fdAlterTime >= '"+getLastSynchroTime()+"'");
		}else{//全量加是否失效的查询条件
			hql = "fdIsAvailable = 1 and fdIsBusiness !=0";
			//selectUrlHql.setWhereBlock("fdIsAvailable = 1");
		}//fd_email like '%@mlandray.com'
		
		if("true".equals(emailIsOnEkp)){
			hql += " and fdEmail like '%" + domainMail + "'";
		}
		
		selectUrlHql.setWhereBlock(hql);
		List<SysOrgPerson> userList = sysOrgPersonService.findValue(selectUrlHql);
		return userList;
	}
	
	/**
	 * 获取需要同步的更新用户
	 * 
	 * @author 严海星
	 * 2018年12月29日
	 * @throws Exception 
	 */
	@SuppressWarnings("unchecked")
	private List<SysOrgPerson> getSynchroUpdateUser(String lastSyncTime,String emailIsOnEkp,String domainMail) throws Exception
	{
		ISysOrgPersonService sysOrgPersonService = (ISysOrgPersonService) SpringBeanUtil.getBean("sysOrgPersonService");
		HQLInfo selectUrlHql = new HQLInfo();
		selectUrlHql.setSelectBlock("sysOrgPerson");
		String hql = "fdCreateTime != fdAlterTime and fdIsBusiness !=0 and fdAlterTime >= '"+lastSyncTime+"'";
		if("true".equals(emailIsOnEkp)){
			hql += " and fdEmail like '%" + domainMail + "'";
		}
		selectUrlHql.setWhereBlock(hql);
		List<SysOrgPerson> userList = sysOrgPersonService.findValue(selectUrlHql);
		return userList;
	}
	
	/**
	 * 获取部门映射信息
	 * @param ekpIdAliIdMap
	 * @throws Exception
	 * @author 严海星
	 * 2019年4月24日
	 */
	@SuppressWarnings("unchecked")
	private void getEkpOrgIdAliOrgIdMap(Map<String,String> ekpIdAliIdMap) throws Exception{
		IAlimailOrgMappingService alimailOrgMappingService = (IAlimailOrgMappingService) SpringBeanUtil.getBean("alimailOrgMappingService");
		HQLInfo selectUrlHql = new HQLInfo();
		selectUrlHql.setSelectBlock("alimailOrgMapping");
		List<AlimailOrgMapping> mappingList = alimailOrgMappingService.findList(selectUrlHql);
		for(AlimailOrgMapping a : mappingList){
			ekpIdAliIdMap.put(a.getFdId(), a.getFdAlimailId());
		}
	}
	
	/**
	 * 执行新增组织架构的同步
	 * @param token
	 * @param orgList
	 * @param ekpIdAliIdMap
	 * @param range		执行范围,1为全量,2为增加量
	 * @throws Exception
	 * @author 严海星
	 * 2019年1月9日
	 * @param token 
	 */
	private String exeSynchroAddOrg(String wsUrl ,String accessTarget ,String token, List<SysOrgElement> orgList,Map<String,String> ekpIdAliIdMap,Integer range)throws Exception
	{
		//如果是全量操作
		if(RANGE_ALL == range){
			//需要判断是否存在初始化生成的映射,需要过滤已在阿里云邮存在的部门
			if(ekpIdAliIdMap.size()>0){
				Set<String> ekpids = ekpIdAliIdMap.keySet();
				Iterator<SysOrgElement> it = orgList.iterator();
				while(it.hasNext()){
					SysOrgElement org = it.next();
					if(ekpids.contains(org.getFdId())){
						it.remove();
					}
				}
			}
		}
		
		
		//获取根部门id
		String rootId = this.getDomainInfo(wsUrl,accessTarget,"rootDepartmentId", token);
		//用来收集成功创建好的组织架构信息(保存ekp组织架构与阿里邮箱组织架构映射信息)
		List<AlimailOrgMapping> orgMappings = new ArrayList<AlimailOrgMapping>();
		//用来收集创建失败的组织架构信息
		List<AlimailSyncFailMapping> createFailMappingList = new ArrayList<AlimailSyncFailMapping>();
		for(SysOrgElement o : orgList)
		{
			String parentId = null;
			if(o.getFdParent() == null){
				parentId = rootId;
			}else{
				if(!StringUtil.isNotNull(ekpIdAliIdMap.get(o.getFdParent().getFdId()))){
					//需要生成部门
					SysOrgElement fdParent = o.getFdParent();
					createAliDept(fdParent,wsUrl,accessTarget,token,ekpIdAliIdMap);
				}
				parentId = ekpIdAliIdMap.get(o.getFdParent().getFdId());
			}
			JSONObject param = new JSONObject();
			param.put("parentId", parentId);
			param.put("name", o.getFdName());
			//排序
			JSONObject extend = new JSONObject();
			extend.put("order", o.getFdOrder());
			param.put("extend", extend);
			JSONObject resultJo = requestToAliServer(accessTarget,token, param, wsUrl+"/ud/createDepartment");
			if("100".equals(resultJo.getJSONObject("status").getString("statusCode"))){
				AlimailOrgMapping orgMapping = new AlimailOrgMapping();
				orgMapping.setFdId(o.getFdId());
				orgMapping.setFdHierarchyId(o.getFdHierarchyId());
				orgMapping.setFdAlimailId(resultJo.getJSONObject("data").getString("departmentId"));
				orgMappings.add(orgMapping);
				
				ekpIdAliIdMap.put(o.getFdId(), orgMapping.getFdAlimailId());
			}else{
				AlimailSyncFailMapping failInfo = new AlimailSyncFailMapping();
				failInfo.setFdEkpId(o.getFdId());
				failInfo.setFdType(CREATE_FAIL_ORG_TYPE);
				failInfo.setFdRequestJson(param.toJSONString());
				failInfo.setFdResponseJson(resultJo.toJSONString());
				failInfo.setFdRequsetTime(DateUtil.convertDateToString(new Date(), "yyyy-MM-dd HH:mm:ss"));
				//判断是否是也存在部门导致创建不了
				if("2963".equals(resultJo.getJSONObject("status").getString("statusCode"))){
					failInfo.setFdIsNeedTry("0");//不需要重试
				}else{
					failInfo.setFdIsNeedTry("1");
				}
				createFailMappingList.add(failInfo);
				logger.info("[阿里云邮]同步失败部门信息:"+resultJo);
			}
		}
		logger.info("[阿里云邮]成功同步新增部门数量:"+orgMappings.size());
		//批量保存ekp组织架构与阿里邮箱组织架构映射信息
		batchSaveOrgMapping(orgMappings,500);
		
		if(createFailMappingList.size()>0){
			logger.error("[阿里云邮]同步创建失败部门数量:"+createFailMappingList.size());
			batchSaveSyncFailMapping(createFailMappingList,500);
		}
		
		return rootId;
	}
	
	/**
	 * 
	 * @param range
	 * @throws Exception
	 * @author 严海星
	 * 2019年1月2日
	 */
	@SuppressWarnings("unchecked")
	private List<SysOrgElement> getSynchroOrg( Integer range) throws Exception
	{
		ISysOrgElementService sysOrgElementService = (ISysOrgElementService) SpringBeanUtil.getBean("sysOrgElementService");
		HQLInfo selectUrlHql = new HQLInfo();
		selectUrlHql.setSelectBlock("sysOrgElement");
		if(RANGE_ALL == range){//全量
			selectUrlHql.setWhereBlock("fdOrgType <=2 and fdIsAvailable = 1");
		}else{//增量
			selectUrlHql.setWhereBlock("fdOrgType <=2 and fdIsAvailable = 1 and fdCreateTime = fdAlterTime and fdAlterTime >= '"+getLastSynchroTime()+"'");
		}
		selectUrlHql.setOrderBy("length(fdHierarchyId)");
		List<SysOrgElement> orglist = sysOrgElementService.findValue(selectUrlHql);
		return orglist;
	}
	
	/**
	 * 批量保存同步错误创建映射
	 * @param failMappings
	 * @param batchCount
	 * @throws Exception
	 * @author 严海星
	 * 2019年4月28日
	 */
	private void batchSaveSyncFailMapping(List<AlimailSyncFailMapping> failMappings , Integer batchCount)throws Exception{
		TransactionStatus txStatus = null;
		try{
			txStatus = TransactionUtils.beginNewTransaction();
			IAlimailSyncFailMappingService alimailSyncFailMappingService = (IAlimailSyncFailMappingService) SpringBeanUtil.getBean("alimailSyncFailMappingService");
			for(int i = 0 ; i < failMappings.size() ; i++)
			{
				alimailSyncFailMappingService.add(failMappings.get(i));
				if(i > 0 && i % batchCount == 0){//每batchCount条执行一次保存
					TransactionUtils.getTransactionManager().commit(txStatus);
					alimailSyncFailMappingService.getBaseDao().getHibernateSession().clear();
					txStatus = TransactionUtils.beginNewTransaction();
				}
			}
		}catch (Exception e) {
			e.printStackTrace();
			TransactionUtils.getTransactionManager().rollback(txStatus);
			throw e;
		}finally {
			if(txStatus != null)
				TransactionUtils.getTransactionManager().commit(txStatus);
		}
	}
	
	/**
	 * 批量保存组织架构同步映射,默认每500条数据做一次保存操作
	 * @param orgMappings
	 * @param batchCount
	 * @throws Exception
	 * @author 严海星
	 * 2019年1月8日
	 */
	private void batchSaveOrgMapping(List<AlimailOrgMapping> orgMappings , Integer batchCount) throws Exception
	{
		
		if(orgMappings.size() > 0){
			logger.debug("[阿里云邮]执行阿里邮箱组织架构同步映射批量保存操作...");
			logger.debug("[阿里云邮]共" + orgMappings.size() + "条组织架构映射数据,需要保存处理.");
			TransactionStatus txStatus = null;
			try{
				txStatus = TransactionUtils.beginNewTransaction();
				IAlimailOrgMappingService alimailOrgMappingService = (IAlimailOrgMappingService) SpringBeanUtil.getBean("alimailOrgMappingService");
				for(int i = 0 ; i < orgMappings.size() ; i++)
				{
					alimailOrgMappingService.add(orgMappings.get(i));
					if(i > 0 && i % batchCount == 0){//每batchCount条执行一次保存
						TransactionUtils.getTransactionManager().commit(txStatus);
						alimailOrgMappingService.getBaseDao().getHibernateSession().clear();
						txStatus = TransactionUtils.beginNewTransaction();
					}
				}
			}catch (Exception e) {
				TransactionUtils.getTransactionManager().rollback(txStatus);
				throw e;
			}finally {
				if(txStatus != null)
					TransactionUtils.getTransactionManager().commit(txStatus);
			}
		}
			
	}
	
	/**
	 * 请求阿里服务
	 * @param token
	 * @param param
	 * @param serverUrl
	 * @return
	 * @throws Exception
	 * @author 严海星
	 * 2019年1月4日
	 */
	private JSONObject requestToAliServer(String accessTarget ,String token,JSONObject param,String serverUrl)throws Exception
	{
		JSONObject requestJo = new JSONObject();
		JSONObject access = new JSONObject();
		access.put("accessToken", token);
		access.put("accessTarget",accessTarget);
		requestJo.put("access", access);
		requestJo.put("param", param);
		
		HttpClientUtil client = new HttpClientUtil();
		String result = client.httpDoPost(serverUrl, requestJo.toJSONString());
		logger.debug("[阿里云邮]阿里邮件请求json:"+requestJo.toJSONString()+";阿里邮件响应json:"+result);
		if(StringUtil.isNotNull(result)){
			return JSON.parseObject(result);
		}
		return null;
	}
	
	/**
	 * 获取主邮箱信息,暂时只用来获取根部门id,key值"rootDepartmentId",具体还要获取什么看api
	 * @param key
	 * @return
	 * @author 严海星
	 * 2019年1月7日
	 * @throws Exception 
	 */
	private String getDomainInfo(String wsUrl ,String accessTarget ,String key,String token) throws Exception
	{
		JSONObject param = new JSONObject();
		JSONArray fields = new JSONArray();
		fields.add("rootDepartmentId");
		param.put("fields", fields);
		
		JSONObject resultJo = requestToAliServer(accessTarget,token,param,wsUrl+"/ud/getDomainInfo");
		
		if("100".equals(resultJo.getJSONObject("status").getString("statusCode"))){
			return resultJo.getJSONObject("data").getString(key);
		}
		return null;
	}
	
	public static void main(String[] args) throws Exception {
		
		//AliMailOrgSynchroService service = new AliMailOrgSynchroService();
		
		//-----------------------------------------
		
		HttpClientUtil hc = new HttpClientUtil();
		String token = hc.getToken("https://alimailws.alibaba-inc.com/alimailws", 
									"mlandraycomws", 
									"mlandray357#123alimail");
		
		JSONObject param = new JSONObject();
		//-------------------------------------------
		
		/*//搜索邮件列表
		String serverUrl = "https://alimailws.alibaba-inc.com/alimailws/mbox/search";
		param.put("searchKey","未读");
		param.put("offset", "0");
		param.put("length", "100");
		JSONArray fields = new JSONArray();
		fields.add("mailId");
		fields.add("subject");
		fields.add("from");
		fields.add("date");
		param.put("fields", fields);*/
		
		//JSONObject queryRoot = JSON.parseObject("{\"left\":{\"middle\":{\"left\":{\"middle\":\"has_read\"},\"middle\":\"equals\",\"right\":{\"middle\":\"0\"}}},\"middle\":\"and\",\"right\":{\"middle\":{\"left\":{\"middle\":\"folder_id\"},\"middle\":\"equals\",\"right\":{\"middle\":\"2\"}}}}");
		//JSONObject queryRoot = JSON.parseObject("{\"left\":{\"left\":{\"left\":{\"middle\":\"has_read\"},\"middle\":\"equals\",\"right\":{\"middle\":\"0\"}},\"middle\":\"and\",\"right\":{\"left\":{\"middle\":\"folder\"},\"middle\":\"equals\",\"right\":{\"middle\":\"2\"}}}}");
		//未读邮件列表
		/*String serverUrl = "https://alimailws.alibaba-inc.com/alimailws/mbox/searchEx";
		JSONObject queryRoot = JSON.parseObject("{\"left\":{\"middle\":\"has_read\"},\"middle\":\"equals\",\"right\":{\"middle\":\"0\"}}");
		param.put("offset", "0");
		param.put("length", "100");
		param.put("queryRoot", queryRoot);
		JSONArray fields = new JSONArray();
		fields.add("folderId");
		fields.add("mailId");
		fields.add("subject");
		fields.add("from");
		fields.add("date");
		param.put("fields", fields);*/
		
		/*
		//创建部门
		param.put("parentId", "----.t----.LQzg9t:2:----.Lssy7M");
		param.put("name", "测试");
		String serverUrl = "https://alimailws.alibaba-inc.com/alimailws/ud/createDepartment";*/
		
		//获取根部门id
		String serverUrl = "https://alimailws.alibaba-inc.com/alimailws/ud/getDomainInfo";
		JSONArray fields = new JSONArray();
		fields.add("rootDepartmentId");
		param.put("fields", fields);
		
		
		/*JSONObject param = JSON.parseObject("{\"names\":[{\"extend\":{\"ex1\":\"ex1\"},\"name\":[\"应用集成部\",\"流程集成部\"]},{\"extend\":{\"ex1\":\"ex1\"},\"name\":[\"应用集成部\",\"tic集成部\"]}]}");

		//新增部门
		String serverUrl = "https://alimailws.alibaba-inc.com/alimailws/ud/createDepartmentByName";*/
		
		
		/*//更新部门
		String serverUrl = "https://alimailws.alibaba-inc.com/alimailws/ud/updateDepartment";
		param.put("departmentId", "----.t----.LQzg9t:2:----.Lsxvr9");
		//param.put("parentId", "");
		param.put("name", "蓝凌机构");*/
		
		/*//新增用户
		String serverUrl = "https://alimailws.alibaba-inc.com/alimailws/ud/createAccounts";
		JSONArray accounts = new JSONArray();
		for(int i = 0 ; i < 2 ; i ++)
		{
			JSONObject account = new JSONObject();
			account.put("name", "张三"+i);
			account.put("passwd", "zs123456");
			account.put("email", "zs"+i+"@mlandray.com");
			account.put("departmentId", "----.t----.LQzg9t:2:----.Lsxvr9");
			accounts.add(account);
		}
		param.put("accounts", accounts);
		//{"data":{"fail":[],"success":[{"email":"zs0@mlandray.com"},{"email":"zs1@mlandray.com"}]},"status":{"statusCode":"100","statusMsg":"SYS_RESP_OK"}}
*/
		
		/*String serverUrl = "https://alimailws.alibaba-inc.com/alimailws/ud/updateAccountInfo";
		param.put("email", "yanhx2@mlandray.com");*/
		
	
		//更新用户状态
		/*String serverUrl = "https://alimailws.alibaba-inc.com/alimailws/ud/updateAccountsStatus";
		JSONArray accounts = new JSONArray();
		JSONObject account = new JSONObject();
		account.put("status", "0");
		//account.put("userRole", "0");
		account.put("email", "admin@mlandray.com");
		accounts.add(account);
		param.put("accounts", accounts);*/
		
		/*//强制删除用户
		String serverUrl = "https://alimailws.alibaba-inc.com/alimailws/ud/removeAccounts";
		JSONArray emails = new JSONArray();
		emails.add("admin@mlandray.com");
		param.put("emails", emails);*/
		
		//查询用户
		//{"data":{"fail":[],"dataList":[{"email":"yanhx@mlandray.com"}]},"status":{"statusCode":"100","statusMsg":"SYS_RESP_OK"}}
		//{"data":{"fail":[{"errorCode":"1602","errorMsg":"APP_UD_ACCOUNT_NOT_EXIST","email":"yanhx1@mlandray.com"}],"dataList":[]},"status":{"statusCode":"100","statusMsg":"SYS_RESP_OK"}}
		/*String serverUrl = "https://alimailws.alibaba-inc.com/alimailws/ud/getAccountsInfo";
		JSONArray emails = new JSONArray();
		emails.add("postmaster@mlandray.com");	
		param.put("emails", emails);
		JSONArray fields = new JSONArray();
		fields.add("email");
		fields.add("createTime");
		fields.add("extend");
		param.put("fields", fields);*/
		
		//获取所有部门列表
		//String serverUrl = "https://alimailws.alibaba-inc.com/alimailws/ud/getDepartmentList";
		
		/*//获取用户信息
		String serverUrl = "https://alimailws.alibaba-inc.com/alimailws/ud/searchAccountEx";
		param.put("filter", "s");
		param.put("offset", "0");
		param.put("length", "100");
		JSONArray fields = new JSONArray();
		fields.add("name");
		fields.add("departmentId");
		fields.add("email");
		param.put("fields", fields);*/
		
		//更改用户密码
		/*String serverUrl = "https://alimailws.alibaba-inc.com/alimailws/ud/updateAccountsPassword";
		JSONObject account = new JSONObject();
		account.put("password", "Aa123456");
		account.put("email", "yanhx@mlandray.com");
		JSONArray accounts = new JSONArray();
		accounts.add(account);
		param.put("accounts", accounts);*/
		
		//---------------------------------------------
		JSONObject requestJo = new JSONObject();
		JSONObject access = new JSONObject();
		access.put("accessToken", token);
		access.put("accessTarget", "mlandray.com");
		//access.put("accessTarget", "yanhx@mlandray.com");
		requestJo.put("access", access);
		requestJo.put("param", param);
		System.out.println("请求json:"+requestJo.toJSONString());
		HttpClientUtil client = new HttpClientUtil();
		String result = client.httpDoPost(serverUrl, requestJo.toJSONString());
		if(StringUtil.isNotNull(result)){
			System.out.println("响应json:"+result);
		}
		//-------------------------------------------------
		
		//String date = DateUtil.formatDate(new Date(), "yyyy-MM-dd HH:mm:ss");
		//System.out.println(date);
		/*String[] dd = {"ddd","ccc","bbb","aaa"};
		JSONArray array = new JSONArray();
		for(String s : dd)
		{
			System.out.println(s);
			array.add(0, s);
		}
		System.out.println(array.toJSONString());*/
		//System.out.println("1".equals(1)?0:1);
		
		/*List<String> list = new ArrayList<String>();
		for(int i = 0 ; i < 10;i++)
		{
			list.add(i+"");
		}
		System.out.println(list);
		ListIterator<String> it = list.listIterator();
		while(it.hasNext()){
			String next = it.next();
			if("2".equals(next))
			{
				it.remove();
			}
		}
		System.out.println(list);*/
		
		/*List<SysOrgElement> ces = new ArrayList<SysOrgElement>();
		SysOrgElement o1 = new SysOrgElement();
		o1.setFdHierarchyId("xxxxx");
		ces.add(o1);
		SysOrgElement o2 = new SysOrgElement();
		o1.setFdHierarchyId("xxxxxxxx");
		ces.add(o2);
		SysOrgElement o3 = new SysOrgElement();
		o1.setFdHierarchyId("xxxxxxxxxxxx");
		ces.add(o3);
		
		for(SysOrgElement o : ces)
		{
			System.out.println(o.getFdHierarchyId());
		}
		Collections.sort(ces,new SortByHierarchyLengthComparator());
		for(SysOrgElement o : ces)
		{
			System.out.println(o.getFdHierarchyId());
		}*/
		
		
		
		
		//----.t----.LQzg9t:2:----.Lud6Wz
		//token   mlandraycomwsQ4TrM1556087458796
		
		/*Map<String,JSONObject> u = new HashMap<String,JSONObject>();
		HttpClientUtil hc = new HttpClientUtil();
		String token = "mlandraycomwsQ4TrM1556087458796";
		String deptId = "----.t----.LQzg9t:2:----.Lud6Wz";
		recursionGetAllUser(u, token, hc, deptId, "@mlandray.com", 0, 1, 0);
		Set<String> keySet = u.keySet();
		for(String name : keySet){
			System.out.println(name);
		}*/
		
		/*List<String> dd = new ArrayList<String>();
		dd.add("xxx1");
		dd.add("xxxaaaa1");
		dd.add("xxxccccccc1");
		dd.add("xxxccccccccccddddddd1");
		Collections.sort(dd,new SortByStringLengthComparator());
		for(String xx : dd){
			System.out.println(xx);
		}*/
		
		
		//设置管理员权 
		/*HttpClientUtil hc = new HttpClientUtil();
		String token = hc.getToken("https://alimailws.alibaba-inc.com/alimailws", 
									"landraycomcnws", 
									"landraY357#123alimail");
		String serverUrl = "https://alimailws.alibaba-inc.com/alimailws/ud/updateAccountInfo";
		JSONObject requestJo = new JSONObject();
		JSONObject access = new JSONObject();
		access.put("accessToken", token);
		//access.put("accessTarget", "yanhx@mlandray.com");//目标邮件
		access.put("accessTarget", "postmaster@landray.com.cn");//目标邮件
		
		JSONObject param = new JSONObject();
		param.put("userRole", "0");
		
		requestJo.put("access", access);
		requestJo.put("param", param);
		System.out.println("请求json:"+requestJo.toJSONString());
		HttpClientUtil client = new HttpClientUtil();
		String result = client.httpDoPost(serverUrl, requestJo.toJSONString());
		System.out.println(result);*/
		
		/*//清空阿里云邮账户
		String code = "landraycomcnws";
		String password = "landraY357#123alimail";
		String accessTarget = "landray.com.cn";
		List<String> ignoreUser = new ArrayList<String>();
		//ignoreUser.add("yanhx@mlandray.com");
		ignoreUser.add("postmaster@landray.com.cn");*/
		
		
		//deleteAllOrgAndUser(ignoreUser,accessTarget,code,password);
		//System.out.println(PasswordUtil.desDecrypt("zbINuAvnVjT3BlogTwgMuA=="));
		//System.out.println(new String(Base64.decodeBase64("zbINuAvnVjT3BlogTwgMuA==".getBytes())));
		//System.out.println(MD5Util.getMD5Str("Le讕鲧"));
	}
	
	/**
	 * 切记该功能只对内特殊使用,删除完成后还需清数据库映射表及删除最后同步时间
	 * 删除所有部门及用户信息
	 * @param ignoreUser
	 * @author 严海星
	 * 2019年4月23日
	 */
	/*private static void deleteAllOrgAndUser(List<String> ignoreUser,String accessTarget,String code,String password){
		System.out.println("删除执行中....................");
		
		HttpClientUtil hc = new HttpClientUtil();
		String wsUrl = "https://alimailws.alibaba-inc.com/alimailws";
		String token = hc.getToken(wsUrl, code,password);
		String serverUrl = "https://alimailws.alibaba-inc.com/alimailws/ud/getDepartmentList";
		
		JSONObject requestJo = new JSONObject();
		JSONObject access = new JSONObject();
		access.put("accessToken", token);
		access.put("accessTarget", accessTarget);
		requestJo.put("access", access);
		requestJo.put("param", new JSONObject());
		
		List<String> deptLongNames = new ArrayList<String>();
		Map<String,String> deptNameMap = new HashMap<String,String>();
		
		String result = hc.httpDoPost(serverUrl, requestJo.toJSONString());
		if(StringUtil.isNotNull(result)){
			JSONObject rejo = JSON.parseObject(result);
			List<String> allDeptId = new ArrayList<String>();
			if("100".equals(rejo.getJSONObject("status").getString("statusCode"))){
				JSONArray deptArray = rejo.getJSONObject("data").getJSONArray("dataList");
				if(deptArray.size() > 0){
					Map<String,JSONObject> deptJoMap = new HashMap<String,JSONObject>();
					for(int i = 0 ; i < deptArray.size() ; i++){
						JSONObject deptJo = deptArray.getJSONObject(i);
						deptJoMap.put(deptJo.getString("departmentId"), deptJo);
						allDeptId.add(deptJo.getString("departmentId"));
						//System.out.println(deptJo.getString("departmentId") + "  --" + deptJo.getString("name"));
					}
					
					for(int i = 0 ; i < deptArray.size() ; i++){
						JSONObject deptJo = deptArray.getJSONObject(i);
						String deptName = deptJo.getString("name");
						if(!(accessTarget.equals(deptName))){//过滤顶级部门
							StringBuilder deptNamesb = new StringBuilder(deptName);
							//递归获取父部门名称用#号间隔,获取后作为key
							AliMailUtil.recursionGetDeptName(deptJo, deptNamesb, deptJoMap, accessTarget);
							deptNameMap.put(deptNamesb.toString(), deptJo.getString("departmentId"));
						}
					}
					
					Set<String> longnames = deptNameMap.keySet();
					for(String name : longnames){
						deptLongNames.add(name);
					}
					System.out.println("部门数量:"+longnames.size());
				}
			}
			//JSONObject param = new JSONObject();
			//排序,最低级部门在前
			Collections.sort(deptLongNames,new SortByStringLengthComparator());
			for(String name : deptLongNames){
				System.out.println(name);
			}
			JSONObject param = new JSONObject();
			//删除部门
			for(String deptlongname : deptLongNames){
				String deptid = deptNameMap.get(deptlongname);
				param.put("departmentId", deptid);
				requestJo.put("param", param);
				result = hc.httpDoPost("https://alimailws.alibaba-inc.com/alimailws/ud/removeDepartment", requestJo.toJSONString());
				JSONObject rjo = JSON.parseObject(result);
				System.out.println(result);
				if(!"100".equals(rjo.getJSONObject("status").getString("statusCode"))){
					System.out.println("error"+result);
				}
			}
			
			List<String> aliUsers = new ArrayList<String>();
			//根据部门获取所有用户
			for(String deptid : allDeptId){
				recursionGetAllUser(aliUsers, token, hc, deptid, accessTarget, 0, 1, 0);
			}
			for(String e : aliUsers){
				System.out.println(e);
			}
			System.out.println("获取用户数量:"+aliUsers.size());
			
			//去除ignore用户
			JSONArray emails = new JSONArray();
			for(String mail : aliUsers){
				if(!ignoreUser.contains(mail)){
					emails.add(mail);
				}
			}
			//批量删除用户
			JSONObject param = new JSONObject();
			param.put("emails", emails);
			requestJo.put("param", param);
			serverUrl = "https://alimailws.alibaba-inc.com/alimailws/ud/removeAccounts";
			result = hc.httpDoPost(serverUrl, requestJo.toJSONString());
			System.out.println(result);
			JSONObject deleUserjo = JSON.parseObject(result);
			if("100".equals(deleUserjo.getJSONObject("status").getString("statusCode"))){
				//排序,最低级部门在前
				//Collections.sort(deptLongNames,new SortByStringLengthComparator());
				param = new JSONObject();
				//删除部门
				for(String deptlongname : deptLongNames){
					String deptid = deptNameMap.get(deptlongname);
					param.put("departmentId", deptid);
					requestJo.put("param", param);
					result = hc.httpDoPost("https://alimailws.alibaba-inc.com/alimailws/ud/removeDepartment", requestJo.toJSONString());
					JSONObject rjo = JSON.parseObject(result);
					if(!"100".equals(rjo.getJSONObject("status").getString("statusCode"))){
						System.out.println(result);
					}
				}
			}
		}
		System.out.println("删除执行完成....请删除数据库映射表信息,及同步更新时间");
	}*/
	
	
	/**
	 * 递归获取所有用户
	 * @param aliUser
	 * @param token
	 * @param hc
	 * @param deptId
	 * @param accessTarget
	 * @param offset
	 * @param length
	 * @param allCount
	 * @author 严海星
	 * 2019年4月24日
	 */
	private static void recursionGetAllUser(List<String> aliUser,
			String token,HttpClientUtil hc,String deptId,String accessTarget,
			Integer offset,Integer length,Integer allCount){
		String serverUrl = "https://alimailws.alibaba-inc.com/alimailws/ud/getAccountList";
		JSONObject requestJo = new JSONObject();
		JSONObject access = new JSONObject();
		access.put("accessToken", token);
		access.put("accessTarget", accessTarget);
		requestJo.put("access", access);
		JSONObject param = new JSONObject();
		param.put("departmentId", deptId);
		param.put("offset", offset);
		param.put("length", length);
		JSONArray fields = new JSONArray();
		fields.add("name");
		fields.add("departmentId");
		fields.add("email");
		param.put("fields", fields);
		requestJo.put("param", param);
		//System.out.println("已获取人员数量:"+aliUser.size());
		//System.out.println("获取人员请求:"+requestJo.toJSONString());
		String result = hc.httpDoPost(serverUrl, requestJo.toJSONString());
		//{"data":{"total":"4","accounts":[{"name":"严海星","email":"yanhx@mlandray.com","departmentId":"----.t----.LQzg9t:2:----.Lssy7M"},{"name":"postmaster","email":"postmaster@mlandray.com","departmentId":"----.t----.LQzg9t:2:----.Lssy7M"},{"name":"管理员","email":"admin@mlandray.com","departmentId":"----.t----.LQzg9t:2:----.Lssy7M"},{"name":"匿名用户","email":"anonymous@mlandray.com","departmentId":"----.t----.LQzg9t:2:----.Lssy7M"}]},"status":{"statusCode":"100","statusMsg":"SYS_RESP_OK"}}
		JSONObject rejo = JSON.parseObject(result);
		if("100".equals(rejo.getJSONObject("status").getString("statusCode"))){
			JSONArray accounts = rejo.getJSONObject("data").getJSONArray("accounts");
			allCount += accounts.size();
			Integer total = Integer.valueOf(rejo.getJSONObject("data").getString("total"));
			if(accounts.size() > 0){
				for(int i = 0 ; i < accounts.size() ; i++){
					JSONObject user = accounts.getJSONObject(i);
					aliUser.add(user.getString("email"));
				}
			}
			if(allCount < total){
				recursionGetAllUser(aliUser,token,hc,deptId,accessTarget,allCount,length,allCount);
			}
		}
	}
	
	/**
	 * 获取需要同步的更新的组织架构
	 * 
	 * @author 严海星
	 * 2018年12月29日
	 * @throws Exception 
	 */
	@SuppressWarnings("unchecked")
	private List<SysOrgElement> getSynchroUpdateOrg(String lastSyncTime) throws Exception
	{
		ISysOrgElementService sysOrgElementService = (ISysOrgElementService) SpringBeanUtil.getBean("sysOrgElementService");
		HQLInfo selectUrlHql = new HQLInfo();
		selectUrlHql.setSelectBlock("sysOrgElement");
		selectUrlHql.setWhereBlock("fdOrgType <=2 and fdCreateTime != fdAlterTime and fdAlterTime >= '"+lastSyncTime+"'");
		selectUrlHql.setOrderBy("length(fdHierarchyId)");
		List<SysOrgElement> orglist = sysOrgElementService.findValue(selectUrlHql);
		return orglist;
	}

	/**
	 * 
	 * @param token
	 * @param email
	 * @return
	 * @author 严海星
	 * 2019年1月14日
	 * @throws Exception 
	 */
	public Map<String, Object> getUnReadMailInfos(String token, String email) throws Exception {
		
		HttpClientUtil hc = new HttpClientUtil();
		//获取服务的wsurl
		String wsUrl = AliMailUtil.getWsUrl();
		if(StringUtil.isNull(token)){
			//获取鉴权token
			token = hc.getToken(wsUrl, AliMailUtil.getCode(), AliMailUtil.getPwd());
		}
		
		JSONObject param = new JSONObject();
		String serverUrl =  AliMailUtil.getWsUrl()+"/mbox/searchEx";
		JSONObject queryRoot = JSON.parseObject("{\"left\":{\"middle\":\"has_read\"},\"middle\":\"equals\",\"right\":{\"middle\":\"0\"}}");
		param.put("offset", "0");
		param.put("length", "100");
		param.put("queryRoot", queryRoot);
		JSONArray fields = new JSONArray();
		fields.add("folderId");
		fields.add("mailId");
		fields.add("subject");
		fields.add("from");
		fields.add("date");
		fields.add("tag");
		fields.add("summary");
		fields.add("searchExtend");
		fields.add("priority");
		fields.add("status");
		fields.add("extend");
		fields.add("extendMailHeaders");
		param.put("fields", fields);
		
		JSONObject resultJo = requestToAliServer(email, token, param, serverUrl);
		String statusCode = resultJo.getJSONObject("status").getString("statusCode");
		if("408".equals(statusCode)){//token失效需要重新获取
			return getUnReadMailInfos(null, email);
		}else if("100".equals(statusCode)){
			Map<String, Object> resultMap = new HashMap<String,Object>();
			List<Map<String,String>> emailList = new ArrayList<Map<String,String>>();
			
			JSONArray datalist = resultJo.getJSONObject("data").getJSONArray("dataList");
			for(int i = 0 ; i < datalist.size() ; i++){
				JSONObject emailJo = datalist.getJSONObject(i);
				if("2".equals(emailJo.getString("folderId"))){
					Map<String,String> emailMap = new HashMap<String,String>();
					emailMap.put("subject", emailJo.getString("subject"));
					emailMap.put("date", emailJo.getString("date"));
					String mailId = emailJo.getString("mailId");
					emailMap.put("url", "/third/mail/alimail/aliMail.do?method=sso&mailId="+mailId);
					emailMap.put("from", emailJo.getString("from"));
					emailList.add(emailMap);
				}
			}
			resultMap.put("token", token);
			resultMap.put("mailList", emailList);
			return resultMap;
		}else if("1602".equals(statusCode)){//不存在该用户
			Map<String, Object> resultMap = new HashMap<String,Object>();
			resultMap.put("token", token);
			resultMap.put("USER_NOT_EXIST", true);
			return resultMap;
		}
		return null;
	}
	
	/**
	 * 初始化映射组织架构
	 * @return
	 * @author 严海星
	 * 2019年4月18日
	 * @throws Exception 
	 */
	@SuppressWarnings("unchecked")
	public Map<String,Object> initOrgMapping() throws Exception{
		Map<String,Object> resultMap;
		Map<String,Object> deptResult = getAlimailAllDept();
		if("success".equals(deptResult.get("status"))){
			//清空映射信息
			IAlimailOrgMappingService alimailOrgMappingService = (IAlimailOrgMappingService) SpringBeanUtil.getBean("alimailOrgMappingService");
			alimailOrgMappingService.deleteAllMapping();
			//获取阿里邮件中所有部门id,用于获取所有账户
			/*List<String> allMailDeptId = (List<String>) deptResult.get("allDeptId");
			String token = (String) deptResult.get("token");
			getAlimailAllUser(allMailDeptId,token);*/
			//
			resultMap = new HashMap<String,Object>();
			//获取阿里所有部门映射信息key:层级名称#号分隔,value:阿里id
			Map<String,String> deptNameMap = (Map<String, String>) deptResult.get("data");
			//获取ekp系统所有的部门信息key:层级名称#号分隔,value:org对象
			Map<String,SysOrgElement> ekpAlldeptMap = getEkpAllDept();
			Set<String> deptNames = deptNameMap.keySet();
			//云邮组织架构映射
			List<AlimailOrgMapping> orgMappings = new ArrayList<AlimailOrgMapping>();
			//映射成功信息
			StringBuilder successInfo = new StringBuilder();
			//映射失败信息
			StringBuilder failInfo = new StringBuilder();
			for(String deptName : deptNames){
				String alimailId = deptNameMap.get(deptName);
				SysOrgElement org = ekpAlldeptMap.get(deptName);
				if(org != null){//找到映射
					AlimailOrgMapping orgMap = new AlimailOrgMapping();
					orgMap.setFdId(org.getFdId());
					orgMap.setFdAlimailId(alimailId);
					orgMap.setFdHierarchyId(org.getFdHierarchyId());
					orgMappings.add(orgMap);
					successInfo.append("建立映射："+deptName+",ekpId:"+org.getFdId()+",mailId:"+alimailId+ "<br>");
				}else{//找不到映射
					failInfo.append("找不到映射："+deptName+",mailId:"+alimailId+ "<br>");
				}
			}
			
			//批量保存ekp组织架构与阿里邮箱组织架构映射信息
			batchSaveOrgMapping(orgMappings,500);
			resultMap.put("successInfo", StringUtil.isNotNull(successInfo.toString())?successInfo.toString():"无");
			resultMap.put("failInfo", StringUtil.isNotNull(failInfo.toString())?failInfo.toString():"无");
			resultMap.put("status", "success");
			
		}else{
			return deptResult;
		}
		return resultMap;
	}
	
	/**
	 * 获取阿里云邮所有账户
	 * @param allMailDeptId
	 * @param token
	 * @param accessTarget
	 * @return
	 * @throws Exception
	 * @author 严海星
	 * 2019年4月26日
	 */
	private List<String> getAlimailAllUser(List<String> allMailDeptId , String token ,String accessTarget) throws Exception {
		List<String> aliUsers = new ArrayList<String>();
		HttpClientUtil hc = new HttpClientUtil();
		for(String deptId : allMailDeptId){
			recursionGetAllUser(aliUsers, token, hc, deptId, accessTarget, 0, 1, 0);
		}
		
		//过滤掉阿里云邮管理员
		Iterator<String> it = aliUsers.iterator();
		while(it.hasNext()){
			String mail = it.next();
			if(mail.startsWith("postmaster@")){
				it.remove();
			}
		}
		
		return aliUsers;
	}

	/**
	 * 获取ekp所有部门信息
	 * @return
	 * @author 严海星
	 * 2019年4月18日
	 * @throws Exception 
	 */
	@SuppressWarnings("unchecked")
	private Map<String,SysOrgElement> getEkpAllDept() throws Exception{
		HQLInfo info = new HQLInfo();
		info.setWhereBlock("sysOrgElement.fdOrgType < 4 and sysOrgElement.fdIsAvailable = 1");
		ISysOrgElementService sysOrgElementService = (ISysOrgElementService) SpringBeanUtil.getBean("sysOrgElementService");
		List<SysOrgElement> eles = sysOrgElementService.findList(info);
		Map<String, SysOrgElement> depts = new HashMap<String, SysOrgElement>();
		for (SysOrgElement e : eles) {
			String hName = e.getFdParentsName("#",
					SysLangUtil.getOfficialLang());
			if (StringUtil.isNull(hName)) {
				hName = e.getFdNameOri();
			} else {
				hName = hName + "#" + e.getFdNameOri();
			}
			depts.put(hName, e);
		}
		return depts;
	}
	
	/**
	 * 获取阿里云邮件所有部门信息
	 * @return
	 * @throws Exception
	 * @author 严海星
	 * 2019年4月18日
	 */
	private Map<String,Object> getAlimailAllDept() throws Exception{
		
		Map<String,Object> resultMap = new HashMap<String,Object>();
		//收集所有部门的id,用于获取所有用户时使用
		//List<String> allDeptId = new ArrayList<String>();
		
		HttpClientUtil hc = new HttpClientUtil();
		//获取服务的wsurl
		String wsUrl = AliMailUtil.getWsUrl();
		//获取鉴权token
		String token = hc.getToken(wsUrl, AliMailUtil.getCode(), AliMailUtil.getPwd());
		if(StringUtil.isNull(token)){
			token = hc.getToken(wsUrl, AliMailUtil.getCode(), AliMailUtil.getPwd());
		}
		if(StringUtil.isNotNull(token)){
			String serverUrl = wsUrl + "/ud/getDepartmentList";
			String topDept = AliMailUtil.getDomainStr().substring(1);
			JSONObject requestJo = new JSONObject();
			JSONObject access = new JSONObject();
			access.put("accessToken", token);
			access.put("accessTarget", topDept);
			requestJo.put("access", access);
			requestJo.put("param", new JSONObject());
			
			String result = hc.httpDoPost(serverUrl, requestJo.toJSONString());
			if(StringUtil.isNotNull(result)){
				JSONObject rejo = JSON.parseObject(result);
				if("100".equals(rejo.getJSONObject("status").getString("statusCode"))){
					JSONArray deptArray = rejo.getJSONObject("data").getJSONArray("dataList");
					if(deptArray.size() > 1){
						Map<String,JSONObject> deptJoMap = new HashMap<String,JSONObject>();
						for(int i = 0 ; i < deptArray.size() ; i++){
							JSONObject deptJo = deptArray.getJSONObject(i);
							deptJoMap.put(deptJo.getString("departmentId"), deptJo);
							//allDeptId.add(deptJo.getString("departmentId"));
						}
						Map<String,String> deptNameMap = new HashMap<String,String>();
						for(int i = 0 ; i < deptArray.size() ; i++){
							JSONObject deptJo = deptArray.getJSONObject(i);
							String deptName = deptJo.getString("name");
							if(!(topDept.equals(deptName))){//过滤顶级部门
								StringBuilder deptNamesb = new StringBuilder(deptName);
								//递归获取父部门名称用#号间隔,获取后作为key
								AliMailUtil.recursionGetDeptName(deptJo, deptNamesb, deptJoMap, topDept);
								deptNameMap.put(deptNamesb.toString(), deptJo.getString("departmentId"));
							}
						}
						resultMap.put("data", deptNameMap);
						//resultMap.put("token", token);
						//resultMap.put("allDeptId", allDeptId);
						resultMap.put("status", "success");
					}else{
						resultMap.put("status", "error");
						resultMap.put("msg", "阿里云邮组织架构为空,不需要初始化设置!");
					}
				}else{
					resultMap.put("status", "error");
					resultMap.put("msg", "获取阿里云邮组织架构失败,请稍后再试!");
				}
			}
		}else{
			resultMap.put("status", "error");
			resultMap.put("msg", "无法获取鉴权token,请稍后再试!");
		}
		return resultMap;
	}
	
}
