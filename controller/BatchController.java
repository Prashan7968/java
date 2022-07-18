package com.maxis.ossisdp.controller;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.maxis.ossisdp.service.boq.BoqService;
import com.maxis.ossisdp.service.dro.DRONewSiteBatchProcessService;
import com.maxis.ossisdp.service.dro.DROUpgradeSiteBatchProcessService;
import com.maxis.ossisdp.service.pcl.NewSiteBatchProcessService;
import com.maxis.ossisdp.service.quote.QuoteService;
import com.maxis.ossisdp.service.su.SUUpgradeBatchProcessService;
import com.maxis.ossisdp.service.tssr.TssrService;
import com.maxis.ossisdp.util.ApplicationConstants;

import lombok.extern.log4j.Log4j2;

@RestController
@Log4j2
public class BatchController {

	@Autowired
	private NewSiteBatchProcessService pclBatchProcessService;

	@Autowired
	private DRONewSiteBatchProcessService droPCLBatchProcessService;

	@Autowired
	private SUUpgradeBatchProcessService upgradeBatchProcessService;

	@Autowired
	private DROUpgradeSiteBatchProcessService droUpgradeSiteBatchProcessService;

	@Autowired
	private TssrService tssrService;

	@Autowired
	private BoqService boqService;

	@Autowired
	private QuoteService quoteService;

	@GetMapping("batch")
	@ResponseBody
	public String triggerBatchProcess(@RequestParam(required = true) String action) {
		ExecutorService executor = Executors.newSingleThreadExecutor();
		switch (action) {
		case "SU":
			executor.execute(new Runnable() {

				@Override
				public void run() {
					upgradeBatchProcessService.processUpgradeSiteIntegration();
				}
			});
			log.info("SU batch process triggered");
			return "SU batch process triggered";
		case "PCL":
			executor.execute(new Runnable() {

				@Override
				public void run() {
					pclBatchProcessService.processNewSiteIntegration();
				}
			});
			log.info("PCL batch process triggered");
			return "PCL batch process triggered";
		case "PCL-DRO":
			executor.execute(new Runnable() {

				@Override
				public void run() {
					droPCLBatchProcessService.processNewSiteDroIntegration();
				}
			});
			log.info("PCL DRO batch process triggered");
			return "PCL DRO batch process triggered";
		case "SU-DRO":
			executor.execute(new Runnable() {

				@Override
				public void run() {
					droUpgradeSiteBatchProcessService.processUpgradeSiteDroIntegration();
				}
			});
			log.info("SU DRO batch process triggered");
			return "SU DRO batch process triggered";
		case "SU-TSSR":
			executor.execute(new Runnable() {

				@Override
				public void run() {
					tssrService.processTSSR(ApplicationConstants.NCR_UPGRADE_SITE);
				}
			});
			log.info("SU TSSR batch process triggered");
			return "SU TSSR batch process triggered";
		case "SU-BOQ":
			executor.execute(new Runnable() {

				@Override
				public void run() {
					boqService.processBoq(ApplicationConstants.NCR_UPGRADE_SITE);
				}
			});
			log.info("SU BOQ batch process triggered");
			return "SU BOQ batch process triggered";
		case "PCL-TSSR":
			executor.execute(new Runnable() {

				@Override
				public void run() {
					tssrService.processTSSR(ApplicationConstants.NCR_NEW_SITE);
				}
			});
			log.info("PCL TSSR batch process triggered");
			return "PCL TSSR batch process triggered";
		case "PCL-BOQ":
			executor.execute(new Runnable() {

				@Override
				public void run() {
					boqService.processBoq(ApplicationConstants.NCR_NEW_SITE);
				}
			});
			log.info("PCL BOQ batch process triggered");
			return "PCL BOQ batch process triggered";
		case "SU-Quote":
			executor.execute(new Runnable() {

				@Override
				public void run() {
					quoteService.processQuote(ApplicationConstants.NCR_UPGRADE_SITE);
				}
			});
			log.info("SU Quote batch process triggered");
			return "SU Quote batch process triggered";
		case "PCL-Quote":
			executor.execute(new Runnable() {

				@Override
				public void run() {
					quoteService.processQuote(ApplicationConstants.NCR_NEW_SITE);
				}
			});
			log.info("PCL Quote batch process triggered");
			return "PCL Quote batch process triggered";
		case "SU-RPA":
			executor.execute(new Runnable() {

				@Override
				public void run() {
					tssrService.triggerFailedRPA();
				}
			});
			log.info("SU failed RPA  batch process triggered");
			return "SU failed RPA  batch process triggered";
		default:
			return "Could not trigger the batch process";
		}
	}

}
