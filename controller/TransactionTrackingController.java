package com.maxis.ossisdp.controller;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.maxis.ossisdp.exception.ISDPAppException;
import com.maxis.ossisdp.payload.Response;
import com.maxis.ossisdp.service.TransactionTrackingService;
import com.maxis.ossisdp.util.ApplicationConstants;

import lombok.extern.log4j.Log4j2;

@RestController
@Log4j2
public class TransactionTrackingController {

	@Autowired
	TransactionTrackingService transactionService;
	
	@GetMapping("/transactiondetails/{lrdOrItemno}")
	@ResponseBody
	public Map<String, List<Map<String, Object>>>  getTransactionDetailsData(@PathVariable(required = true) String lrdOrItemno) {
		log.info("getTransactionDetailsData {}",lrdOrItemno);
		return transactionService.getAllTransactionDetails(lrdOrItemno);
	}

	@GetMapping({"/transactionagingdetails/{lrdOrItemno}","/transactionagingdetails"})
	@ResponseBody
	public Map<String, List<Map<String, Object>>> getTransactionAgingDetailsData(
			 @PathVariable(name = "lrdOrItemno", required = false) String lrdOrItemno) {
		log.info("getTransactionDetailsData {}", lrdOrItemno);
		return transactionService.getAgingTransactionDetails(lrdOrItemno);
	}
	
	@GetMapping("/transactionjoindetails")
	@ResponseBody
	public List<Map> getTransactionDetailsByUnion(@RequestParam(required = true) String lrd) {
		log.info("getTransactionDetailsData {}, {}",lrd);
		return transactionService.getTransactionDetailsByUnion(lrd);
	}
	
	@GetMapping("/getLogDetail/{fileName}/{searchKey}")
	public HashMap<String,List<String>> getLogDetails(@PathVariable(required = true) String fileName,@PathVariable(required = true) String searchKey)  {
		HashMap<String,List<String>> response= new HashMap<>();
		List<String> errorList=new ArrayList<>();
		try {
			return transactionService.getLogDeatils(fileName,searchKey);
		} catch (ISDPAppException e) {
			log.debug(e.getMessage(), e);
			errorList.add(e.getMessage());
			response.put("logDetails",errorList);
			
		} catch (Exception e) {
			log.debug(e.getMessage(), e);
			errorList.add(e.getMessage());
			response.put("logDetails",errorList);
			
		}
		return response;
	}
	
	@GetMapping("/report")
	@ResponseBody
	public Map<Integer, List<Map<String, Object>>> getTransactionreportData() {
		log.info("getTransactionreport");
		return transactionService.getTransactionReport();
	}

}
