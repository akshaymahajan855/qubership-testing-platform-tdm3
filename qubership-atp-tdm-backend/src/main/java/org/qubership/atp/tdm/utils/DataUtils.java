/*
 *  Copyright 2024-2025 NetCracker Technology Corporation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.qubership.atp.tdm.utils;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.qubership.atp.tdm.exceptions.db.TdmDbCheckColumnNameException;
import org.qubership.atp.tdm.exceptions.db.TdmDbCheckQueryException;
import org.qubership.atp.tdm.exceptions.db.TdmDbCheckTableNameException;
import org.qubership.atp.tdm.model.statistics.StatisticsInterval;
import org.springframework.util.FileSystemUtils;

import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DataUtils {

    private static final int UI_SUITABLE_PERIODS = 8;
    private static final int WEEK_LENGTH = 7;
    private static final String WEEK_PLACEHOLDER = "w";
    private static final String DAY_PLACEHOLDER = "d";
    public static StatisticsInterval statisticsInterval;

    /**
     * Preparing cleanup by date string.
     *
     * @param dateConfig - cleanup config date.
     * @return prepared SQL query.
     */
    public static LocalDate calculateExpiredData(@Nonnull String dateConfig) {
        dateConfig = dateConfig.replaceAll("\\s", "");
        int weekPlace = dateConfig.indexOf(WEEK_PLACEHOLDER);
        int weekCount = 0;
        if (weekPlace != -1) {
            weekCount = Integer.parseInt(dateConfig.substring(0, weekPlace));
        }
        int daysPlace = dateConfig.indexOf(DAY_PLACEHOLDER);
        int daysCount = 0;
        if (daysPlace != -1) {
            daysCount = Integer.parseInt(dateConfig.substring(weekPlace + 1, daysPlace));
        }
        return LocalDate.now().minusWeeks(weekCount).minusDays(daysCount);
    }

    /**
     * Get statistics interval.
     *
     * @param dateFrom - beginning time.
     * @param dateTo   - ending time.
     * @return - parsed dates list.
     */
    public static List<String> getStatisticsInterval(@Nonnull LocalDate dateFrom, @Nonnull LocalDate dateTo) {
        List<String> dates = new ArrayList<>();
        double days = ChronoUnit.DAYS.between(dateFrom, dateTo);
        long weeks = (long) Math.ceil(days / WEEK_LENGTH);
        Period period = Period.between(dateFrom, dateTo);
        if (period.getYears() == 0) {
            if (period.getMonths() == 0 && period.getDays() < UI_SUITABLE_PERIODS) {
                for (int i = 0; i <= period.getDays(); ++i) {
                    dates.add(dateFrom.plusDays(i).format(DateTimeFormatter.ofPattern(
                            DateFormatters.UI_DATE_FORMATTER_DAYS)));
                }
                statisticsInterval = StatisticsInterval.DAYS;
            } else if (weeks < UI_SUITABLE_PERIODS) {
                for (int i = 1; i <= weeks; ++i) {
                    dates.add(dateFrom.plusWeeks(i - 1).format(DateTimeFormatter.ofPattern(
                            DateFormatters.UI_DATE_FORMATTER_DAYS))
                            + " - "
                            + dateFrom.plusWeeks(i).minusDays(1)
                            .format(DateTimeFormatter.ofPattern(DateFormatters.UI_DATE_FORMATTER_DAYS)));
                }
                statisticsInterval = StatisticsInterval.WEEKS;
            } else {
                for (int i = 0; i <= period.getMonths(); ++i) {
                    dates.add(dateFrom.plusMonths(i).format(DateTimeFormatter.ofPattern(
                            DateFormatters.UI_DATE_FORMATTER_MONTHS)));
                }
                statisticsInterval = StatisticsInterval.MONTHS;
            }
        } else {
            for (int i = 0; i <= period.getYears(); ++i) {
                dates.add(dateFrom.plusYears(i).format(DateTimeFormatter.ofPattern(
                        DateFormatters.UI_DATE_FORMATTER_YEARS)) + " year");
            }
            statisticsInterval = StatisticsInterval.YEARS;
        }
        return dates;
    }

    /**
     * Get time stamp start and end of current day.
     * @return Map with stored time stamps.
     */
    public static Map<String, String> generateTimeStampDailyRange(String timeZone) {
        ZoneId zoneId = ZoneId.of(timeZone);
        LocalDate today = LocalDate.now(zoneId);
        String zdtStart = today.atStartOfDay(zoneId)
                .toString().replaceAll("(:[0]{2}[\\[][GMT+]{4}[0-9]{2}:[0-9]{2}[]]$)", "");
        String zdtStop = today.plusDays(1).atStartOfDay(zoneId)
                .toString().replaceAll("(:[0]{2}[\\[][GMT+]{4}[0-9]{2}:[0-9]{2}[]]$)", "");
        Map<String, String> m = new HashMap<>(2, 1.1f);
        m.put("startTimeStamp", zdtStart);
        m.put("endTimeStamp", zdtStop);
        return m;
    }

    /**
     * Delete file.
     */
    public static void deleteFile(Path path) {
        if (path != null) {
            log.debug("delete path {}", path);

            try {
                FileSystemUtils.deleteRecursively(path);
            } catch (IOException var3) {
                log.error("Cannot delete path {} ", path, var3);
            }
        }
    }

    /**
     * Check parameters.
     */
    public static void checkColumnName(String params) {
        if (params != null && params.contains("\"")) {
            log.error(String.format(TdmDbCheckColumnNameException.DEFAULT_MESSAGE, params));
            throw new TdmDbCheckColumnNameException(params);
        }
    }

    /**
     * Check table name.
     */
    public static void checkTableName(String name) {
        if (name != null) {
            Pattern pattern = Pattern.compile("^[a-zA-Z_0-9]+$");
            Matcher matcher = pattern.matcher(name);
            if (!matcher.find()) {
                log.error(String.format(TdmDbCheckTableNameException.DEFAULT_MESSAGE, name));
                throw new TdmDbCheckTableNameException(name);
            }
        }
    }

    /**
     * Check query.
     */
    public static void checkQuery(String query) {
        if (query != null) {
            String[] errorWord = {"truncate ", "drop ", "delete "};
            if (Arrays.stream(errorWord).anyMatch(word -> query.toLowerCase().contains(word.toLowerCase()))) {
                log.error(TdmDbCheckQueryException.DEFAULT_MESSAGE);
                throw new TdmDbCheckQueryException();
            }
        }
    }
}
