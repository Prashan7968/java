package com.maxis.ossisdp.controller;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.maxis.ossisdp.mysql.entity.MysqlServiceTransactionLog;
import com.maxis.ossisdp.mysql.entity.boq.BoqTransactionLog;
import com.maxis.ossisdp.mysql.entity.pcl.PCLTransactionLog;
import com.maxis.ossisdp.mysql.repository.MysqlServiceTransactionLogRepo;
import com.maxis.ossisdp.mysql.repository.pcl.PCLTransactionLogRepo;
import com.maxis.ossisdp.payload.Response;
import com.maxis.ossisdp.payload.boq.BoqRequest;
import com.maxis.ossisdp.service.boq.BoqService;
import com.maxis.ossisdp.service.boq.BoqStatus;
import com.maxis.ossisdp.service.boq.BoqTransactionService;
import com.maxis.ossisdp.service.quote.QuoteStatus;
import com.maxis.ossisdp.util.ApplicationConstants;
import com.maxis.ossisdp.util.DBConnectionCheck;
import com.maxis.ossisdp.util.EmailHelper;
import com.maxis.ossisdp.util.NCRHelper;

import lombok.extern.log4j.Log4j2;

@RestController
@Log4j2
public class BoqController {

	@Autowired
	private BoqService boqService;

	@Autowired
	BoqTransactionService boqTransactionService;

	@Autowired
	private MysqlServiceTransactionLogRepo suTransactionLogRepo;

	@Autowired
	private PCLTransactionLogRepo pclTransactionLogRepo;

	@Autowired
	private NCRHelper ncrHelper;

	@Autowired
	@Qualifier("oracleJdbcTemplate")
	private JdbcTemplate jdbcTemplate;

	@Autowired
	EmailHelper emailHelper;

	// TODO remove temp start//
	@Value("${su.tssr.lrd.names}")
	String suLrdnames;

	@Value("${pcl.tssr.lrd.names}")
	String pclLrdnames;

	@Value("${su.boq.transfer.enable}")
	boolean suEnable;

	@Value("${pcl.boq.transfer.enable}")
	boolean pclEnable;
	// TODO remove temp end //

	@Autowired
	private DBConnectionCheck dbConnectionCheck;
	
	@Value("${transfer.error.cc.email.ids}")
	String ccEmailId;

	@PostMapping("boq")
	@ResponseBody
	public Response processBoq(final @RequestHeader HttpHeaders headers, final @RequestBody BoqRequest boqRequest)
			throws Exception {
		log.debug("Inside process BOQ");
		Response maxisResponse;
		try {
			if (StringUtils.isEmpty(boqRequest.getTransactionID())) {
				maxisResponse = new Response();
				maxisResponse.setReturnStatus(ApplicationConstants.STATUS_E);
				maxisResponse.setReturnDescription("Transaction ID is null");
				SimpleDateFormat format = new SimpleDateFormat(ApplicationConstants.HW_DATE_FORMAT);
				maxisResponse.setReturnDateTime(format.format(new Date()));
				return maxisResponse;
			}

			log.info("Received BOQ transaction # " + boqRequest.getTransactionID() + ", Item Name :"
					+ boqRequest.getItemNo());

			// TODO remove temp start //
			List<String> allLRD = new ArrayList<String>();
			boolean isEnable = true;
			if (StringUtils.isEmpty(boqRequest.getItemScenario())) {
				isEnable = false;
			} else if (ApplicationConstants.NCR_UPGRADE_SITE.equals(boqRequest.getItemScenario())) {
				allLRD = (suLrdnames == null || suLrdnames.isEmpty()) ? new ArrayList<String>()
						: Stream.of(suLrdnames.split(",", -1)).collect(Collectors.toList());
				if (!suEnable) {
					isEnable = false;
				}
			} else if (ApplicationConstants.NCR_NEW_SITE.equals(boqRequest.getItemScenario())) {
				allLRD = (pclLrdnames == null || pclLrdnames.isEmpty()) ? new ArrayList<String>()
						: Stream.of(pclLrdnames.split(",", -1)).collect(Collectors.toList());
				if (!pclEnable) {
					isEnable = false;
				}
			}

			if (!isEnable) {
				log.info("Error BOQ transaction # " + boqRequest.getTransactionID() + ", Item Name :"
						+ boqRequest.getItemNo());
				maxisResponse = new Response();
				maxisResponse.setReturnStatus(ApplicationConstants.STATUS_E);
				maxisResponse.setReturnDescription("BOQ transfer not supported");
				SimpleDateFormat format = new SimpleDateFormat(ApplicationConstants.HW_DATE_FORMAT);
				maxisResponse.setReturnDateTime(format.format(new Date()));
				return maxisResponse;
			}

			// TODO remove temp end //

			String pclSuName = boqRequest.getItemNo();
			String type = boqRequest.getItemScenario();

			if (StringUtils.isEmpty(pclSuName) || ApplicationConstants.NCR_UPGRADE_SITE.equals(type)) {
				List<MysqlServiceTransactionLog> transList = suTransactionLogRepo
						.findObjsByNameAndActionOrderByDate(pclSuName, "SU");
				if (transList == null || transList.isEmpty()) {
					maxisResponse = new Response();
					maxisResponse.setReturnStatus(ApplicationConstants.STATUS_E);
					String message = "SU was not transferred via system";
					maxisResponse.setReturnDescription(message);
					SimpleDateFormat format = new SimpleDateFormat(ApplicationConstants.HW_DATE_FORMAT);
					maxisResponse.setReturnDateTime(format.format(new Date()));
					log.info("SU Error BOQ transaction # {} , Item # {}", boqRequest.getTransactionID(),
							boqRequest.getItemNo());
					sendEmail(boqRequest, message);
					return maxisResponse;
				} else {
					maxisResponse = checkBOQStatus(boqRequest.getItemNo(), boqRequest.getTransactionID());
					if (maxisResponse != null) {
						return maxisResponse;
					}
				}
				// TODO remove temp start //
				MysqlServiceTransactionLog trans = transList.get(0);
				log.info("Trans : " + trans.getItem() + " LRD " + trans.getLrd());
				if (!(allLRD.contains("ALL") || allLRD.contains(trans.getLrd()))) {
					log.info("Error BOQ transaction # " + boqRequest.getTransactionID() + ", Item Name :"
							+ boqRequest.getItemNo());
					maxisResponse = new Response();
					maxisResponse.setReturnStatus(ApplicationConstants.STATUS_E);
					maxisResponse.setReturnDescription(trans.getLrd() + " Not supported");
					SimpleDateFormat format = new SimpleDateFormat(ApplicationConstants.HW_DATE_FORMAT);
					maxisResponse.setReturnDateTime(format.format(new Date()));
					return maxisResponse;
				}
				// TODO remove temp end //
			} else if (StringUtils.isEmpty(pclSuName) || ApplicationConstants.NCR_NEW_SITE.equals(type)) {
				List<PCLTransactionLog> pclLogs = pclTransactionLogRepo.findObjByNameOrderByDate(pclSuName);
				if (pclLogs == null || pclLogs.isEmpty()) {
					maxisResponse = new Response();
					maxisResponse.setReturnStatus(ApplicationConstants.STATUS_E);
					String message = "PCL was not transferred via system";
					maxisResponse.setReturnDescription(message);
					SimpleDateFormat format = new SimpleDateFormat(ApplicationConstants.HW_DATE_FORMAT);
					maxisResponse.setReturnDateTime(format.format(new Date()));
					log.info("PCL not transfer Error BOQ transaction # {} , Item # {}", boqRequest.getTransactionID(),
							boqRequest.getItemNo());
					sendEmail(boqRequest, message);
					return maxisResponse;
				} else {
					maxisResponse = checkBOQStatus(boqRequest.getItemNo(), boqRequest.getTransactionID());
					if (maxisResponse != null) {
						return maxisResponse;
					}
				}
				// TODO remove temp start//
				PCLTransactionLog pclLog = pclLogs.get(0);
				log.info("Trans : " + pclLog.getName() + " LRD " + pclLog.getLrd());

				boolean found = false;
				String[] lrds = pclLog.getLrd().split(",");
				for (int i = 0; i < lrds.length; i++) {
					if (!(allLRD.contains("ALL") || allLRD.contains(lrds[i]))) {
						found = true;
					}
				}

				if (found) {
					log.info("Error BOQ transaction # " + boqRequest.getTransactionID() + ", Item Name :"
							+ boqRequest.getItemNo());
					maxisResponse = new Response();
					maxisResponse.setReturnStatus(ApplicationConstants.STATUS_E);
					maxisResponse.setReturnDescription(pclLog.getLrd() + " Not supported");
					SimpleDateFormat format = new SimpleDateFormat(ApplicationConstants.HW_DATE_FORMAT);
					maxisResponse.setReturnDateTime(format.format(new Date()));
					return maxisResponse;
				}
				// TODO remove temp end //
			}

			if (dbConnectionCheck.isDatabaseDown()) {
				log.info("NCR DB Down cannot process BOQ transaction # " + boqRequest.getTransactionID()
						+ ", Item Name :" + boqRequest.getItemNo());
				maxisResponse = new Response();
				maxisResponse.setReturnStatus(ApplicationConstants.STATUS_E);
				maxisResponse.setReturnDescription("NCR Down");
				SimpleDateFormat format = new SimpleDateFormat(ApplicationConstants.HW_DATE_FORMAT);
				maxisResponse.setReturnDateTime(format.format(new Date()));
				return maxisResponse;
			}

			boqTransactionService.logBoqTransaction(boqRequest, BoqStatus.RECEIVED, "");
			try {
				ExecutorService executor = Executors.newSingleThreadExecutor();
				executor.execute(new Runnable() {
					@Override
					public void run() {
						boqService.validateBoq(boqRequest.getTransactionID());
					}
				});
			} catch (Exception e) {
				log.debug(e.getMessage(), e);
				boqTransactionService.saveBoqRequest(boqRequest, BoqStatus.RECEIVE_FAILED, e.getMessage());
				maxisResponse = new Response();
				maxisResponse.setReturnStatus(ApplicationConstants.STATUS_E);
				maxisResponse.setReturnDescription(e.getMessage());
				SimpleDateFormat format = new SimpleDateFormat(ApplicationConstants.HW_DATE_FORMAT);
				maxisResponse.setReturnDateTime(format.format(new Date()));
				return maxisResponse;
			}
		} catch (Exception e) {
			log.debug(e.getMessage(), e);
			maxisResponse = new Response();
			maxisResponse.setReturnStatus(ApplicationConstants.STATUS_E);
			maxisResponse.setReturnDescription(e.getMessage());
			SimpleDateFormat format = new SimpleDateFormat(ApplicationConstants.HW_DATE_FORMAT);
			maxisResponse.setReturnDateTime(format.format(new Date()));
			return maxisResponse;
		}

		maxisResponse = new Response();
		maxisResponse.setReturnStatus(ApplicationConstants.STATUS_Y);
		maxisResponse.setReturnDescription(ApplicationConstants.HW_RETURN_OK_DESCRIPTION);
		SimpleDateFormat format = new SimpleDateFormat(ApplicationConstants.HW_DATE_FORMAT);
		maxisResponse.setReturnDateTime(format.format(new Date()));
		return maxisResponse;
	}

	private void sendEmail(BoqRequest boqRequest, String message) {
		try {
			BigDecimal object_id = jdbcTemplate.queryForObject(
					"select object_id from netcracker.nc_objects where name='" + boqRequest.getItemNo() + "'",
					BigDecimal.class);
			if (object_id != null) {
				String createdBy = ncrHelper.getPCLCreatedBy(String.valueOf(object_id), boqRequest.getItemScenario());
				String[] toEmail = { (createdBy + "@maxis.com.my") };
				String emailSubject = boqRequest.getCboqDatas().get(0).getLrd6() + " " + boqRequest.getItemNo()
						+ " - SU/PCL is not transferred via system";
				String emailContent = "Hi " + " RF/Opti,<br>" + message + "<br>" + "Thanks.";
				emailHelper.sendMimeMessage(toEmail, ccEmailId , emailSubject, emailContent);
			}
		} catch (Exception e) {
			// Do nothing
		}
	}

	private Response checkBOQStatus(String boqRequestItemNo, String boqRequestTransactionID) {
		Response maxisResponse = null;
		List<String> statusList = new ArrayList<String>();
		statusList.add(BoqStatus.VALIDATION_COMPLETED.getValue());
		statusList.add(BoqStatus.BOQ_SUCCESS.getValue());
		statusList.add(QuoteStatus.QUOTE_VALIDATION_FAILED.getValue());
		statusList.add(QuoteStatus.QUOTE_SUCCESS.getValue());

		List<BoqTransactionLog> boqList = boqTransactionService
				.findByitemNoAndByStatusOrderByCreatedOn(boqRequestItemNo, statusList);
		if (boqList.size() > 0) {
			BoqTransactionLog logTransactionLog = boqList.get(0);

			if (BoqStatus.VALIDATION_COMPLETED.getValue().equalsIgnoreCase(logTransactionLog.getStatus())) {
				maxisResponse = new Response();
				maxisResponse.setReturnStatus(ApplicationConstants.STATUS_E);
				String message = "BOQ request is already in progress";
				maxisResponse.setReturnDescription(message);
				SimpleDateFormat format = new SimpleDateFormat(ApplicationConstants.HW_DATE_FORMAT);
				maxisResponse.setReturnDateTime(format.format(new Date()));
				log.info("SU BOQ in progress Error TSSR transaction # {} , Item # {}", boqRequestTransactionID,
						boqRequestItemNo);
				return maxisResponse;
			} else if ((BoqStatus.BOQ_SUCCESS.getValue().equalsIgnoreCase(logTransactionLog.getStatus()))
					|| (QuoteStatus.QUOTE_SUCCESS.getValue().equalsIgnoreCase(logTransactionLog.getStatus()))) {
				maxisResponse = new Response();
				maxisResponse.setReturnStatus(ApplicationConstants.STATUS_E);
				String message = "BOQ request successfully processed already";
				maxisResponse.setReturnDescription(message);
				SimpleDateFormat format = new SimpleDateFormat(ApplicationConstants.HW_DATE_FORMAT);
				maxisResponse.setReturnDateTime(format.format(new Date()));
				log.info("SU BOQ request successfully processed already Error TSSR transaction # {} , Item # {}",
						boqRequestTransactionID, boqRequestItemNo);
				return maxisResponse;
			}
		}
		return maxisResponse;
	}
}
