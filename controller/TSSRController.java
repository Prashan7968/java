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

import com.maxis.ossisdp.exception.ISDPAppException;
import com.maxis.ossisdp.mysql.entity.MysqlServiceTransactionLog;
import com.maxis.ossisdp.mysql.entity.pcl.PCLTransactionLog;
import com.maxis.ossisdp.mysql.entity.tssr.TssrTransactionHistLog;
import com.maxis.ossisdp.mysql.entity.tssr.TssrTransactionLog;
import com.maxis.ossisdp.mysql.repository.MysqlServiceTransactionLogRepo;
import com.maxis.ossisdp.mysql.repository.pcl.PCLTransactionLogRepo;
import com.maxis.ossisdp.payload.Response;
import com.maxis.ossisdp.payload.tssr.TssrRequest;
import com.maxis.ossisdp.service.tssr.TssrService;
import com.maxis.ossisdp.service.tssr.TssrStatus;
import com.maxis.ossisdp.service.tssr.TssrTransactionHistoryService;
import com.maxis.ossisdp.service.tssr.TssrTransactionService;
import com.maxis.ossisdp.util.ApplicationConstants;
import com.maxis.ossisdp.util.DBConnectionCheck;
import com.maxis.ossisdp.util.EmailHelper;
import com.maxis.ossisdp.util.NCRHelper;

import lombok.extern.log4j.Log4j2;

@RestController
@Log4j2
public class TSSRController {

	@Autowired
	private TssrService tssrService;

	@Autowired
	private TssrTransactionService tssrTransService;

	@Autowired
	private TssrTransactionHistoryService tssrTransactionHistoryService;

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

	@Value("${su.tssr.transfer.enable}")
	boolean suEnable;

	@Value("${pcl.tssr.transfer.enable}")
	boolean pclEnable;
	// TODO remove temp end //

	@Autowired
	private DBConnectionCheck dbConnectionCheck;
	
	@Value("${transfer.error.cc.email.ids}")
	String ccEmailId;

	@PostMapping("tssr")
	@ResponseBody
	public Response processTSSR(final @RequestHeader HttpHeaders headers, final @RequestBody TssrRequest tssrRequest)
			throws Exception {
		log.debug("Inside process TSSR");
		Response maxisResponse;
		try {
			if (StringUtils.isEmpty(tssrRequest.getTransactionID())) {
				maxisResponse = new Response();
				maxisResponse.setReturnStatus(ApplicationConstants.STATUS_E);
				maxisResponse.setReturnDescription("Transaction ID is null");
				SimpleDateFormat format = new SimpleDateFormat(ApplicationConstants.HW_DATE_FORMAT);
				maxisResponse.setReturnDateTime(format.format(new Date()));
				return maxisResponse;
			}

			log.info("Received TSSR transaction # " + tssrRequest.getTransactionID() + ", Item Name :"
					+ tssrRequest.getItemNo());

			// TODO remove temp start //
			List<String> allLRD = new ArrayList<String>();
			boolean isEnable = true;
			if (StringUtils.isEmpty(tssrRequest.getItemScenario())) {
				isEnable = false;
			} else if (ApplicationConstants.NCR_UPGRADE_SITE.equals(tssrRequest.getItemScenario())) {
				allLRD = (suLrdnames == null || suLrdnames.isEmpty()) ? new ArrayList<String>()
						: Stream.of(suLrdnames.split(",", -1)).collect(Collectors.toList());
				if (!suEnable) {
					isEnable = false;
				}
			} else if (ApplicationConstants.NCR_NEW_SITE.equals(tssrRequest.getItemScenario())) {
				allLRD = (pclLrdnames == null || pclLrdnames.isEmpty()) ? new ArrayList<String>()
						: Stream.of(pclLrdnames.split(",", -1)).collect(Collectors.toList());
				if (!pclEnable) {
					isEnable = false;
				}
			}

			if (!isEnable) {
				log.info("Error TSSR transaction # " + tssrRequest.getTransactionID() + ", Item Name :"
						+ tssrRequest.getItemNo());
				maxisResponse = new Response();
				maxisResponse.setReturnStatus(ApplicationConstants.STATUS_E);
				maxisResponse.setReturnDescription("TSSR transfer not supported");
				SimpleDateFormat format = new SimpleDateFormat(ApplicationConstants.HW_DATE_FORMAT);
				maxisResponse.setReturnDateTime(format.format(new Date()));
				return maxisResponse;
			}

			// TODO remove temp end //

			String pclSuName = tssrRequest.getItemNo();
			String type = tssrRequest.getItemScenario();

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
					log.info("SU Error TSSR transaction # {} , Item # {}", tssrRequest.getTransactionID(),
							tssrRequest.getItemNo());
					sendEmail(tssrRequest, message);
					return maxisResponse;
				} else {
					maxisResponse = checkTSSRStatus(tssrRequest.getItemNo(), tssrRequest.getTransactionID());
					if (maxisResponse != null) {
						return maxisResponse;
					}
				}
				// TODO remove temp start //
				MysqlServiceTransactionLog trans = transList.get(0);
				log.info("Trans : " + trans.getItem() + " LRD " + trans.getLrd());
				if (!(allLRD.contains("ALL") || allLRD.contains(trans.getLrd()))) {
					log.info("Error TSSR transaction # " + tssrRequest.getTransactionID() + ", Item Name :"
							+ tssrRequest.getItemNo());
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
					log.info("PCL not transfer Error TSSR transaction # {} , Item # {}", tssrRequest.getTransactionID(),
							tssrRequest.getItemNo());
					sendEmail(tssrRequest, message);
					return maxisResponse;
				} else {
					maxisResponse = checkTSSRStatus(tssrRequest.getItemNo(), tssrRequest.getTransactionID());
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
					log.info("Error TSSR transaction # " + tssrRequest.getTransactionID() + ", Item Name :"
							+ tssrRequest.getItemNo());
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
				log.info("NCR DB Down cannot process TSSR transaction # " + tssrRequest.getTransactionID()
						+ ", Item Name :" + tssrRequest.getItemNo());
				maxisResponse = new Response();
				maxisResponse.setReturnStatus(ApplicationConstants.STATUS_E);
				maxisResponse.setReturnDescription("NCR Down");
				SimpleDateFormat format = new SimpleDateFormat(ApplicationConstants.HW_DATE_FORMAT);
				maxisResponse.setReturnDateTime(format.format(new Date()));
				return maxisResponse;
			}

			tssrTransService.logTssrTransaction(tssrRequest, TssrStatus.RECEIVED, "TSSR received");
			tssrTransService.saveTssrDocument(tssrRequest);

			ExecutorService executor = Executors.newSingleThreadExecutor();
			executor.execute(new Runnable() {
				@Override
				public void run() {
					tssrService.validateTSSR(tssrRequest.getTransactionID());
				}
			});

		} catch (ISDPAppException e) {
			log.debug(e.getMessage(), e);
			tssrTransService.saveTssrTransactionLog(tssrRequest, TssrStatus.RECEIVE_FAILED, e.getMessage());
			maxisResponse = new Response();
			maxisResponse.setReturnStatus(ApplicationConstants.STATUS_E);
			maxisResponse.setReturnDescription(e.getMessage());
			SimpleDateFormat format = new SimpleDateFormat(ApplicationConstants.HW_DATE_FORMAT);
			maxisResponse.setReturnDateTime(format.format(new Date()));
			return maxisResponse;
		} catch (Exception e) {
			log.debug(e.getMessage(), e);
			tssrTransService.saveTssrTransactionLog(tssrRequest, TssrStatus.RECEIVE_FAILED, e.getMessage());
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

	private void sendEmail(TssrRequest tssrRequest, String message) {
		try {
			BigDecimal object_id = jdbcTemplate.queryForObject(
					"select object_id from netcracker.nc_objects where name='" + tssrRequest.getItemNo() + "'",
					BigDecimal.class);
			if (object_id != null) {
				String createdBy = ncrHelper.getPCLCreatedBy(String.valueOf(object_id), tssrRequest.getItemScenario());
				String[] toEmail = { (createdBy + "@maxis.com.my") };
				String emailSubject = tssrRequest.getTssrData().getWsd().getNetworkElement() + " "
						+ tssrRequest.getItemNo() + " - SU/PCL is not transferred via system";
				String emailContent = "Hi " + " RF/Opti,<br>" + message + "<br>" + "Thanks.";
				emailHelper.sendMimeMessage(toEmail, ccEmailId , emailSubject, emailContent);
			}
		} catch (Exception e) {
			// Do nothing
		}
	}

	private Response checkTSSRStatus(String tssrRequestItemNo, String tssrRequestTransactionID) {
		Response maxisResponse = null;
		List<String> statuses = new ArrayList<String>();
		statuses.add(TssrStatus.VALIDATION_COMPLETED.getValue());
		statuses.add(TssrStatus.RPA_IN_PROGRESS.getValue());
		statuses.add(TssrStatus.WSD_READY.getValue());
		statuses.add(TssrStatus.RPA_PREPARE_FAILED.getValue());
		List<TssrTransactionLog> tssrList = tssrTransService.findByitemNoAndByStatuses(tssrRequestItemNo, statuses);
		if (tssrList.size() > 0) {
			maxisResponse = new Response();
			maxisResponse.setReturnStatus(ApplicationConstants.STATUS_E);
			String message = "TSSR request is already in progress";
			maxisResponse.setReturnDescription(message);
			SimpleDateFormat format = new SimpleDateFormat(ApplicationConstants.HW_DATE_FORMAT);
			maxisResponse.setReturnDateTime(format.format(new Date()));
			log.info("SU TSSR in progress Error TSSR transaction # {} , Item # {}", tssrRequestTransactionID,
					tssrRequestItemNo);
			return maxisResponse;
		}

		List<TssrTransactionHistLog> tssrHistList = tssrTransactionHistoryService
				.findByitemNoAndByStatus(tssrRequestItemNo, TssrStatus.TSSR_SUCCESS.getValue());
		if (tssrHistList.size() > 0) {
			maxisResponse = new Response();
			maxisResponse.setReturnStatus(ApplicationConstants.STATUS_E);
			String message = "TSSR request successfully processed already";
			maxisResponse.setReturnDescription(message);
			SimpleDateFormat format = new SimpleDateFormat(ApplicationConstants.HW_DATE_FORMAT);
			maxisResponse.setReturnDateTime(format.format(new Date()));
			log.info("SU TSSR request successfully processed already Error TSSR transaction # {} , Item # {}",
					tssrRequestTransactionID, tssrRequestItemNo);
			return maxisResponse;
		}
		return maxisResponse;
	}

	// TODO tssr pagination
//	@GetMapping("tssrList")
//	@ResponseBody
//	public Page<TssrTransactionLog> findAllTSSR(Pageable pageable, String searchText) {
//		return tssrTransService.findAll(pageable, searchText);
//	}
//	
//	@GetMapping
//	@ResponseBody
//	public Page<TssrTransactionLog> findAllTSSR(int pageNumber, int pageSize, String sortBy, String sortDir) {
//		return tssrTransService.findAll(PageRequest.of(
//				pageNumber, pageSize,
//				sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending()
//		));
//	}
}
