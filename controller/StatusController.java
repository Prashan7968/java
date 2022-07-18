package com.maxis.ossisdp.controller;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.maxis.ossisdp.dto.tssr.TssrHistResponse;
import com.maxis.ossisdp.exception.ISDPAppException;
import com.maxis.ossisdp.mysql.entity.tssr.TssrNCRHistoryLog;
import com.maxis.ossisdp.payload.HWStatusResponse;
import com.maxis.ossisdp.payload.Response;
import com.maxis.ossisdp.service.ncr.NCRStatusService;
import com.maxis.ossisdp.service.tssr.TssrService;
import com.maxis.ossisdp.service.tssr.TssrTransactionService;
import com.maxis.ossisdp.util.ApplicationConstants;
import com.maxis.ossisdp.util.DBConnectionCheck;

import lombok.extern.log4j.Log4j2;

@RestController
@Log4j2
public class StatusController {

	@Autowired
	private NCRStatusService ncrStatusService;

	@Autowired
	private TssrTransactionService tssrTransService;

	@Autowired
	private TssrService tssrService;

	@Autowired
	private DBConnectionCheck dbConnectionCheck;

	@PostMapping("status")
	@ResponseBody
	public Response isdpStatusResponse(final @RequestHeader HttpHeaders headers,
			final @RequestBody HWStatusResponse hwResponse) throws Exception {
		Response maxisResponse = null;

		log.debug("Status received : " + hwResponse.toString());

		if (StringUtils.isEmpty(hwResponse.getTransactionID())) {
			maxisResponse = new Response();
			maxisResponse.setReturnStatus(ApplicationConstants.STATUS_E);
			maxisResponse.setReturnDescription("Transaction ID is missing");
			SimpleDateFormat format = new SimpleDateFormat(ApplicationConstants.HW_DATE_FORMAT);
			maxisResponse.setReturnDateTime(format.format(new Date()));
			return maxisResponse;
		}

		// DB check
		if (dbConnectionCheck.isDatabaseDown()) {
			log.info("NCR DB Down cannot process Status received for item object id {} , name {} , itemScenario {} ",
					hwResponse.getTransactionID(), hwResponse.getItemNo(), hwResponse.getItemScenario());
			maxisResponse = new Response();
			maxisResponse.setReturnStatus(ApplicationConstants.STATUS_E);
			maxisResponse.setReturnDescription("NCR Down");
			SimpleDateFormat format = new SimpleDateFormat(ApplicationConstants.HW_DATE_FORMAT);
			maxisResponse.setReturnDateTime(format.format(new Date()));
			return maxisResponse;
		}

		try {
			log.info("Status received for item object id {} , name {} , itemScenario {} ",
					hwResponse.getTransactionID(), hwResponse.getItemNo(), hwResponse.getItemScenario());

			ncrStatusService.processISDPStatusResponse(hwResponse);
			maxisResponse = new Response();
			maxisResponse.setReturnStatus(ApplicationConstants.STATUS_Y);
			maxisResponse.setReturnDescription(ApplicationConstants.HW_RETURN_OK_DESCRIPTION);
			SimpleDateFormat format = new SimpleDateFormat(ApplicationConstants.HW_DATE_FORMAT);
			maxisResponse.setReturnDateTime(format.format(new Date()));
		} catch (ISDPAppException e) {
			log.debug(e.getMessage(), e);
			maxisResponse = new Response();
			maxisResponse.setReturnStatus(ApplicationConstants.STATUS_E);
			maxisResponse.setReturnDescription(e.getMessage());
			SimpleDateFormat format = new SimpleDateFormat(ApplicationConstants.HW_DATE_FORMAT);
			maxisResponse.setReturnDateTime(format.format(new Date()));
		} catch (Exception e) {
			log.debug(e.getMessage(), e);
			maxisResponse = new Response();
			maxisResponse.setReturnStatus(ApplicationConstants.STATUS_E);
			maxisResponse.setReturnDescription("Unknown system error");
			SimpleDateFormat format = new SimpleDateFormat(ApplicationConstants.HW_DATE_FORMAT);
			maxisResponse.setReturnDateTime(format.format(new Date()));
		}
		return maxisResponse;
	}

	@GetMapping("ncrolddatastatus")
	@ResponseBody
	public TssrNCRHistoryLog ncrOldDataStatusResponse(final @RequestParam(name = "transactionID") String transactionId)
			throws Exception {
		return tssrTransService.findNCROldDataByTransactionID(transactionId);
	}

	@GetMapping("tssrstatus")
	@ResponseBody
	public TssrHistResponse tssrstatusStatusResponse(
			final @RequestParam(name = "transactionID") Optional<String> transactionId,
			final @RequestParam(name = "itemNo") Optional<String> itemNo) throws Exception {
		if (itemNo.isPresent()) {
			return tssrTransService.getTransactionHistoryByItemNo(itemNo.get());
		}

		if (transactionId.isPresent()) {
			return tssrTransService.getTransactionHistoryByTransactionId(transactionId.get());
		}

		return null;
	}

	@PostMapping("rpastatus")
	@ResponseBody
	public String rpaStatusResponse(final @RequestHeader HttpHeaders headers,
			final @RequestBody HWStatusResponse hwResponse) {
		log.info("RPA Status received for {} : response {} ", hwResponse.getItemNo(), hwResponse.toString());
		ExecutorService executor = Executors.newSingleThreadExecutor();
		executor.execute(new Runnable() {

			@Override
			public void run() {
				try {
					tssrService.processRPAStatus(hwResponse);
				} catch (ISDPAppException e) {
					log.error(e);
				}
			}
		});
		return "Success";
	}
}
