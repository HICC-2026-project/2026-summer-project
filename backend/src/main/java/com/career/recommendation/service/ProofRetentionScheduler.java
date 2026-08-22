package com.career.recommendation.service;

import com.career.recommendation.entity.PasserData;
import com.career.recommendation.repository.PasserDataRepository;
import com.career.recommendation.util.ServiceTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 검수가 끝난 제보의 증빙 이미지를 보관 기한 뒤 삭제한다.
 *
 * 증빙(합격 통보 캡처)은 검수 근거일 뿐 비교 데이터가 아니다. 승인·반려 뒤에도 디스크에 남겨두면
 * 개인정보(이름·회사명이 찍힌 이미지)를 필요 이상 보관하는 셈이라 기한을 둔다. 스펙 데이터 자체는
 * 익명이므로 그대로 남긴다. 파일 삭제가 먼저, 성공한 것만 DB의 증빙 메타를 비운다 — 반대로 하면
 * 디스크에 고아 파일이 남는다(재배포로 파일이 이미 없으면 메타만 비운다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProofRetentionScheduler {

    private final PasserDataRepository passerDataRepository;
    private final LocalProofStorageService proofStorageService;

    @Value("${app.storage.proof-retention-days:30}")
    private int retentionDays;

    @Scheduled(cron = "0 20 0 * * *", zone = ServiceTime.ZONE)
    @Transactional
    public void purgeExpiredProofs() {
        int purged = purgeReviewedBefore(LocalDateTime.now(ServiceTime.ZONE_ID).minusDays(retentionDays));
        if (purged > 0) {
            log.info("보관 기한({}일)이 지난 검수 완료 제보 증빙 {}건을 삭제했습니다.", retentionDays, purged);
        }
    }

    /** 테스트·수동 실행용. 기준 시각 이전에 검수된 제보의 증빙을 지우고 건수를 돌려준다. */
    @Transactional
    public int purgeReviewedBefore(LocalDateTime before) {
        List<PasserData> expired = passerDataRepository.findReviewedWithProofBefore(before);
        int purged = 0;
        for (PasserData report : expired) {
            proofStorageService.deleteQuietly(report.getProofStoredName());
            report.setProofStoredName(null);
            report.setProofOriginalName(null);
            report.setProofContentType(null);
            report.setProofFileSize(null);
            purged++;
        }
        return purged;
    }
}
