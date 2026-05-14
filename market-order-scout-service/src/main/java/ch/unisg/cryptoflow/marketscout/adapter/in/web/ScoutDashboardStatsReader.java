package ch.unisg.cryptoflow.marketscout.adapter.in.web;

import ch.unisg.cryptoflow.marketscout.domain.ScoutDashboardStats;

import java.util.List;

public interface ScoutDashboardStatsReader {
    List<ScoutDashboardStats> allStats();
}
