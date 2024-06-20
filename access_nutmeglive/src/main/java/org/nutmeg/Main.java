package org.nutmeg;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import static java.util.Arrays.stream;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Slf4j
public class Main implements CommandLineRunner {
    private static final String MAP_CUSTOMER_IDS_TO_INTERNAL_ACCOUNT_SQL = "select " +
            "   twa.ACCOUNTS_UUID_OWN ,  twa.INTERNALACCOUNTREF, 'Standard' as type, twf.OPERATIONAL_STATUS  " +
            " from " +
            "  T_WEB_ACCOUNT twa " +
            " inner join T_WEB_FUND twf on twf.FUNDS_UUID_OWN =twa.UUID  " +
            " where " +
            "    twf.GOAL_TYPE <>'UNALLOCATED' and  twf.GOAL_TYPE <>'PENDING_WITHDRAWAL' and " +
            "    twa.ACCOUNTS_UUID_OWN = ? group by twa.INTERNALACCOUNTREF " +
            "UNION ALL " +
            " select " +
            "  twa2.ACCOUNTS_UUID_OWN, twa2.INTERNALACCOUNTREF, 'JISA' as type, twf.OPERATIONAL_STATUS   " +
            " from " +
            "  T_WEB_ACCOUNT twa2 " +
            " inner join T_PAY_STRIPERELATIONSHIPS tps on tps.userIdRelatedTo = twa2.ACCOUNTS_UUID_OWN " +
            " inner join T_WEB_FUND twf on twf.FUNDS_UUID_OWN = twa2.UUID " +
            " where " +
            "  twf.GOAL_TYPE ='JISA' and tps.userIdRelatedFrom = ? " +
            "group by twa2.INTERNALACCOUNTREF " +
            " " +
            "  ;";
    private static final String GET_ACCOUNT_STATUS_FROM_CUSTOMER_ID = "select " +
            "   twf.OPERATIONAL_STATUS, twf.GOAL_TYPE,  twa.INTERNALACCOUNTREF, 'Standard' as type  " +
            " from " +
            "  T_WEB_ACCOUNT twa " +
            " inner join T_WEB_FUND twf on twf.FUNDS_UUID_OWN =twa.UUID  " +
            " where " +
            "    twf.GOAL_TYPE <>'UNALLOCATED' and  twf.GOAL_TYPE <>'PENDING_WITHDRAWAL' and " +
            "    twa.ACCOUNTS_UUID_OWN = ? group by twa.INTERNALACCOUNTREF " +
            "UNION ALL " +
            " select " +
            " twf.OPERATIONAL_STATUS, twf.GOAL_TYPE,  twa2.INTERNALACCOUNTREF, 'JISA' as type  " +
            " from " +
            "  T_WEB_ACCOUNT twa2 " +
            " inner join T_PAY_STRIPERELATIONSHIPS tps on tps.userIdRelatedTo = twa2.ACCOUNTS_UUID_OWN " +
            " inner join T_WEB_FUND twf on twf.FUNDS_UUID_OWN = twa2.UUID " +
            " where " +
            "  twf.GOAL_TYPE ='JISA' and tps.userIdRelatedFrom = ? " +
            "group by twa2.INTERNALACCOUNTREF " +
            " " +
            "  ;";

    private static final String GET_ACCOUNT_STATUS_FROM_CUSTOMER_ID_OLD = "select " + " twf.OPERATIONAL_STATUS, twf.GOAL_TYPE, twa2.INTERNALACCOUNTREF " + "from " + " T_WEB_ACCOUNT twa2 " + " inner join T_WEB_FUND twf on twf.FUNDS_UUID_OWN = twa2.UUID " + " inner join T_PAY_STRIPERELATIONSHIPS tps on tps.userIdRelatedTo =twa2.ACCOUNTS_UUID_OWN " + "where tps.userIdRelatedFrom = ? COLLATE utf8mb4_unicode_ci;";
    private static final String HEADER_GENERATE_COMP = "INSERT INTO T_NUT_CASHJOURNALNOM (UUID, \n" +
            "                                  CREATEDAT, \n" +
            "                                  UPDATEDAT, \n" +
            "                                  APPLIEDDATE, \n" +
            "                                  REQUESTEDDATE, \n" +
            "                                  VALUEDATE, \n" +
            "                                  JOURNALTYPE, \n" +
            "                                  INTERNALACCOUNT, \n" +
            "                                  WRAPPERTYPE, \n" +
            "                                  AMOUNT, \n" +
            "                                  NARRATIVE, \n" +
            "                                  FUNDUUID, \n" +
            "                                  INTERNALREFERENCE, \n" +
            "                                  OPERATIONALSTATUS) " +
            " " +
            "SELECT JOURNAL_UUID, \n" +
            "       UNIX_TIMESTAMP() AS CREATEDAT, \n" +
            "       UNIX_TIMESTAMP() AS UPDATEDAT, \n" +
            "       'XXX'       AS APPLIEDDATE, \n" +
            "       'XXX'       AS REQUESTEDDATE, \n" +
            "       'XXX'       AS VALUEDATE, \n" +
            "       'EXG'            AS JOURNALTYPE, \n" +
            "       INTERNALACCOUNT, \n" +
            "       WRAPPERTYPE, \n" +
            "       AMOUNT, \n" +
            "       NARRATIVE, \n" +
            "       FUND_UUID, \n" +
            "       INTERNALREFERENCE, \n" +
            "       'PENDING'        AS OP_STATUS " +
            "FROM (SELECT CONVERT(INTERNALACCOUNT USING latin1) AS INTERNALACCOUNT, \n" +
            "                     CONVERT(WRAPPERTYPE USING latin1)     AS WRAPPERTYPE, \n" +
            "                     AMOUNT, \n" +
            "                     NARRATIVE, \n" +
            "                     INTERNALREFERENCE, \n" +
            "                     UUID                                  AS JOURNAL_UUID, \n" +
            "                     FUND_UUID " +
            "      FROM (";
    private static final String FIRST_SELECT_GENERATE_COMP = "SELECT 'AAA' AS INTERNALACCOUNT, \n" +
            "                'BBB' AS WRAPPERTYPE, \n" +
            "                CCC AS AMOUNT, \n" +
            "                'DDD' AS NARRATIVE, \n" +
            "                UUID() AS INTERNALREFERENCE, \n" +
            "                UUID() AS UUID, \n" +
            "                'EEE' AS FUND_UUID";
    private static final String SECOND_SELECT_GENERATE_COMP = "            UNION ALL SELECT 'AAA','BBB',CCC,'DDD',UUID(),UUID(),'EEE'";
    private static final String GET_FEES_FOR_INTERNALACCOUNT = "select " + " INTERNALACCOUNT , \n" + "tna2.FUND_UUID_OWN, \n" + " tna2.wrappertype, \n" + " type, \n" + " VALUE, \n" + " tna2.`DATE` " + "from " + " T_NUT_ACCOUNTPOSTINGX tna2 " + "where  " + " tna2.SIGNEDTYPE = 'FEE' " + " and tna2.`DATE` >= ? " + " and tna2.`DATE` <= ? " + " and (XXX) order by INTERNALACCOUNT, tna2.`DATE`; ";
    private static final String GET_UNALLOCATED_FUND = "SELECT * FROM T_WEB_FUND twf \n" +
            "inner join T_WEB_ACCOUNT twa on twf.FUNDS_UUID_OWN =twa.UUID \n" +
            "where twa.CUSTODIAN_ACCOUNT_NUMBER =? and twf.GOAL_TYPE ='UNALLOCATED' and twf.OPERATIONAL_STATUS ='ACTIVE';";
    // Update date
    private static final String UP_TO_DATE = "2023-02-27";
    // Update list of models (e.g. for smart-alpha)
    private static final List<String> MODEL_LIST = List.of("M2J", "M4J", "M6J", "M8J", "M10J");
    // Update asset code
    private static final String ASSET_CODE = "0504245";
    private final Map<String, Pair<String, String>> customerStartEndDate = new HashMap<>();
    private final Map<String, List<String>> customerIdToInternalAccountIDs = new HashMap<>();
    private final Map<String, List<InternalAccountFees>> customerIdToFees = new HashMap<>();
    private final Map<String, List<FundStatus>> customerIdToFundStatus = new HashMap<>();
    @Value("${ssh-host}")
    private String sshHost;
    @Value("${ssh-port}")
    private int sshPort;
    @Value("${ssh-username}")
    private String sshUsername;
    @Value("${private-key-path}")
    private String privateKeyPath;
    @Value("${passphrase}")
    private String passphrase;
    @Value("${db-nutmeglive-host}")
    private String dbHost;
    @Value("${db-nutmeglive-port}")
    private int dbPort;
    @Value("${db-nutmeglive-username}")
    private String dbUsername;
    @Value("${db-nutmeglive-password}")
    private String dbPassword;
    @Value("${db-nutmeglive-database}")
    private String databaseName;
    private int totalCustomerIDs = 0;
    private int totalInternalAccountRefs = 0;

    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }

    private static String convertListToJDBCList(String column, Collection<String> input) {
        StringBuilder sb = new StringBuilder();
        for (String str : input) {
            sb.append(column + " = ");
            sb.append('\'').append(str).append('\'').append(" OR ");
        }
        return sb.substring(0, sb.length() - 4);
    }

    private void parseCustomerCSVFile() throws FileNotFoundException, ParseException {
        File file = new File("/Users/nicholas.wright/Downloads/Referred Accounts for Remediation.csv");
        try (CSVParser parser = new CSVParser(new FileReader(file), CSVFormat.DEFAULT.builder().setHeader("customerid", "first_inv_date", "last_inv_date").setSkipHeaderRecord(true).build())) {
            Iterator<CSVRecord> recordIterator = parser.stream().iterator();
            while (recordIterator.hasNext()) {
                CSVRecord csvRecord = recordIterator.next();

                String customerID = csvRecord.get(0);
                Pair<String, String> customerRecord = Pair.of(convertDateToAccountPostingDate(csvRecord.get(1)), convertDateToAccountPostingDate(csvRecord.get(2)));
                customerStartEndDate.put(customerID, customerRecord);

                totalCustomerIDs++;
            }
        } catch (final IllegalArgumentException | IOException ex) {
            log.error(ex.getMessage(), ex);
        }
    }

    private void mapCustomerIdsToInternalAccountIDs(Connection conn) throws SQLException, InterruptedException {
        for (String customerId : customerStartEndDate.keySet()) {
            try (PreparedStatement ps = conn.prepareStatement(MAP_CUSTOMER_IDS_TO_INTERNAL_ACCOUNT_SQL)) {
                ps.setString(1, customerId);
                ps.setString(2, customerId);
                try (ResultSet res = ps.executeQuery()) {
                    List<String> internalAccountRefs = new ArrayList<>();
                    while (res.next()) {
                        internalAccountRefs.add(res.getString("INTERNALACCOUNTREF"));
                    }
                    if (internalAccountRefs.isEmpty()) {
                        log.error(String.format("Unable to find INTERNALACCOUNTREF for customer id %s", customerId));
                    }
                    totalInternalAccountRefs += internalAccountRefs.size();

                    customerIdToInternalAccountIDs.put(customerId, internalAccountRefs);
                }
            }


            Thread.sleep(1000);
        }
    }

    private void populateFundStatus(Connection conn) throws SQLException, InterruptedException {
        for (String customerId : customerStartEndDate.keySet()) {
            try (PreparedStatement ps = conn.prepareStatement(GET_ACCOUNT_STATUS_FROM_CUSTOMER_ID)) {
                ps.setString(1, customerId);
                ps.setString(2, customerId);
                List<FundStatus> statuses = new ArrayList<>();
                try (ResultSet res = ps.executeQuery()) {
                    while (res.next()) {
                        FundStatus status = FundStatus.builder()
                                .internalAccountRef(res.getString("INTERNALACCOUNTREF"))
                                .status(res.getString("OPERATIONAL_STATUS"))
                                .goalType(res.getString("GOAL_TYPE"))
                                .build();
                        statuses.add(status);
                    }
                }
                customerIdToFundStatus.put(customerId, statuses);
            }

            Thread.sleep(1000);
        }
    }

    private String getGetUnallocatedFund(Connection conn, String custodianAccountNumber) throws SQLException, InterruptedException {
        try (PreparedStatement ps = conn.prepareStatement(GET_UNALLOCATED_FUND)) {
            ps.setString(1, custodianAccountNumber);
            try (ResultSet res = ps.executeQuery()) {
                while (res.next()) {
                    return res.getString("UUID");
                }
            }
        }

        Thread.sleep(1000);

        throw new IllegalStateException("Internal Account "+custodianAccountNumber+" does not have an active unallocated cash pot");
    }


    private void queryAccountPostingForFees(Connection conn) throws SQLException, InterruptedException {
        int numberCompleted = 0;
        for (String customerId : customerStartEndDate.keySet()) {
            Pair<String, String> startDateEndDate = customerStartEndDate.get(customerId);
            List<String> internalAccountRefs = customerIdToInternalAccountIDs.get(customerId);
            String query = GET_FEES_FOR_INTERNALACCOUNT.replace("XXX", convertListToJDBCList("tna2.INTERNALACCOUNT", internalAccountRefs));
            try (PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, startDateEndDate.getLeft());
                ps.setString(2, startDateEndDate.getRight());
                List<InternalAccountFees> internalAccountFees = new ArrayList<>();
                try (ResultSet res = ps.executeQuery()) {
                    while (res.next()) {
                        InternalAccountFees fee = InternalAccountFees
                                .builder()
                                .internalAccountRef(res.getString("INTERNALACCOUNT"))
                                .fee(res.getBigDecimal("VALUE"))
                                .date(res.getString("DATE"))
                                .type(res.getString("TYPE"))
                                .wrapper(res.getString("WRAPPERTYPE"))
                                .fundUuid(res.getString("FUND_UUID_OWN"))
                                .build();
                        addCompensationToFee(fee, BigDecimal.valueOf(0.059));
                        internalAccountFees.add(fee);
                    }
                    if (internalAccountFees.isEmpty()) {
                        log.error("Unable to find fees for customerId=" + customerId + " - " + ps.toString() + " - " + res.getWarnings());
                    }
                }
                customerIdToFees.put(customerId, internalAccountFees);
                numberCompleted++;
            }


            Thread.sleep(1000);
            System.out.print("\rProcessed " + numberCompleted + " customer IDs");
        }
        log.info(" Completed getting fees");
    }

    private void addCompensationToFee(InternalAccountFees fee, BigDecimal percentageIncrease) {
        fee.setCompensationDue(fee.getFee().multiply(percentageIncrease).setScale(2, RoundingMode.UP));
    }

    private void generateCSVReport() throws IOException {
        try (FileWriter fw = new FileWriter("/Users/nicholas.wright/Downloads/fee-report.csv")) {
            CSVPrinter csvPrinter = new CSVPrinter(fw, CSVFormat.DEFAULT.withHeader("customerID", "startDate", "endDate", "interalAccountRefs", "fees", "compensation", "closing/closed"));

            BigDecimal absTotal = BigDecimal.ZERO;
             BigDecimal compTotal = BigDecimal.ZERO;
            for (String customerId : customerStartEndDate.keySet()) {
                Pair<String, String> startDateEndDate = customerStartEndDate.get(customerId);
                List<InternalAccountFees> fees = customerIdToFees.get(customerId);
                List<FundStatus> statuses = customerIdToFundStatus.get(customerId);
                Map<String, BigDecimal> totalFees = separateFees(fees);
                Map<String, BigDecimal> compensation = separateCompensation(fees);
                absTotal = absTotal.add(totalFees.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
                compTotal = compTotal.add(fees.stream().map(fee->fee.getCompensationDue()).reduce(BigDecimal.ZERO, BigDecimal::add));

                String closedClosing = "";
                List<FundStatus> closedClosingStatus = statuses.stream().filter(a -> a.getStatus().equals("CLOSING") || a.getStatus().equals("CLOSED") || a.getStatus().equals("SAVED") || a.getStatus().equals("DRAFT")).toList();
                String feesStr = formatFees(totalFees);
                String compensationStr = formatFees(compensation);

                // Of all the FundStatuses that are CLOSING/CLOSED/etc, do we actually have any fees for them?
                List<FundStatus> closingFundsThatAreUsed = closedClosingStatus.stream().filter(fs -> feesStr.contains(fs.getInternalAccountRef())).toList();
                if ( !closingFundsThatAreUsed.isEmpty() ){
                    if (isClosingClosedSaved(customerId)) {
                        closedClosing = closedClosingStatus.get(0).getStatus();
                    }
                }

                StringBuilder feeDestination = new StringBuilder();
                for( String internalAccountRef : customerIdToInternalAccountIDs.get(customerId)) {
                    if (feesStr.contains(internalAccountRef)) {
                        if (isInternalAccountClosing(statuses, internalAccountRef)) {
                            feeDestination.append("UNALLOCATED").append('\n');
                        } else {
                            feeDestination.append("Savings").append('\n');
                        }
                    }
                }

                csvPrinter.printRecord(customerId, startDateEndDate.getLeft(), startDateEndDate.getRight(), customerIdToInternalAccountIDs.get(customerId).toString(), feesStr, compensationStr, closedClosing, feeDestination.toString() );
            }

            csvPrinter.printRecord("", "", "", "", "", "");
            csvPrinter.printRecord("", "", "", "", "£"+absTotal.toString() + " (excluding compensation)", "£"+compTotal + " (total compensation)", "£"+absTotal.add(compTotal)+" (total)");

        }
    }

    final String DATE = "20240529";
    private void generatePostingsSQL(Connection connection, String fileName, String narrative, Function<List<InternalAccountFees>, Map<String, BigDecimal>> separateFees) throws IOException, SQLException, InterruptedException {
        boolean firstSelect = false;

        try (FileWriter fw = new FileWriter(fileName)) {
            PrintWriter pw = new PrintWriter(fw);
            pw.println("-- TRAPS-751 Fee Remediation");
            String compHeader = HEADER_GENERATE_COMP.replace("XXX", DATE);
            pw.println(compHeader);

            for (String customerId : customerStartEndDate.keySet()) {
                List<InternalAccountFees> fees = customerIdToFees.get(customerId);
                List<FundStatus> statuses = customerIdToFundStatus.get(customerId);
                Map<String, BigDecimal> totalFees = separateFees.apply(fees);

                for( Map.Entry<String, BigDecimal> entry : totalFees.entrySet()){
                    String internalAccountRef = entry.getKey().substring(0, entry.getKey().indexOf(':'));
                    String wrapper = entry.getKey().substring(entry.getKey().indexOf(':')+1, entry.getKey().lastIndexOf(':'));
                    String fundId = entry.getKey().substring(entry.getKey().lastIndexOf(':')+1);

                    if ( isInternalAccountClosing(statuses, internalAccountRef)) {
                        String newFundId = getGetUnallocatedFund(connection, internalAccountRef);
                        log.info("Found new Unallocated Cash fund for "+internalAccountRef+" - "+newFundId);
                        fundId = newFundId;
                    }

                    if (!firstSelect) {
                        extracted(FIRST_SELECT_GENERATE_COMP, narrative, entry, internalAccountRef, wrapper, fundId, pw);
                        firstSelect = true;
                    } else {
                        extracted(SECOND_SELECT_GENERATE_COMP, narrative, entry, internalAccountRef, wrapper, fundId, pw);
                    }
                }
            }

            pw.println("     ) csvcontent) csvfile;");
        }
    }

    private static void extracted(String template, String narrative, Map.Entry<String, BigDecimal> entry, String internalAccountRef, String wrapper, String fundId, PrintWriter pw) {
        String firstSelectTemplate = template.replace("AAA", internalAccountRef);
        firstSelectTemplate = firstSelectTemplate.replace("BBB", wrapper);
        firstSelectTemplate = firstSelectTemplate.replace("CCC", entry.getValue().toString() );
        firstSelectTemplate = firstSelectTemplate.replace("DDD", narrative + fundId);
        firstSelectTemplate = firstSelectTemplate.replace("EEE", fundId);

        pw.println(firstSelectTemplate);
    }

    private String formatFees(Map<String, BigDecimal> totalFees) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, BigDecimal> entry : totalFees.entrySet()) {
            String[] parts = entry.getKey().split(":");
            sb.append(parts[0]).append(" (").append(parts[1]).append(")").append(" = £").append(entry.getValue()).append('\n');
        }
        return sb.substring(0, Math.max(0, sb.length() - 1));
    }

    private String formatStatus(List<FundStatus> statuses) {
        StringBuilder sb = new StringBuilder();
        for (FundStatus status : statuses) {
            sb.append(status.getInternalAccountRef()).append("{status=").append(status.getStatus()).append(",goal=").append(status.getGoalType()).append("} ");
        }
        return sb.substring(0, Math.max(0, sb.length() - 1));
    }

    private String convertDateToAccountPostingDate(String csvDate) throws ParseException {
        SimpleDateFormat sdr = new SimpleDateFormat("dd/MM/yyyy");
        Date existingDate = sdr.parse(csvDate);
        return new SimpleDateFormat("yyyyMMdd").format(existingDate);
    }

    private static Map<String, BigDecimal> separateFees(List<InternalAccountFees> fees) {
        Map<String, BigDecimal> feeDetails = new HashMap<>();
        for (InternalAccountFees fe : fees) {
            BigDecimal total = feeDetails.get(fe.getInternalAccountRef() + ":" + fe.getWrapper() + ":" + fe.getFundUuid());
            if (total == null) {
                feeDetails.put(fe.getInternalAccountRef() + ":" + fe.getWrapper() + ":" + fe.getFundUuid(), fe.getFee());
                continue;
            }
            feeDetails.put(fe.getInternalAccountRef() + ":" + fe.getWrapper() + ":" + fe.getFundUuid(), total.add(fe.getFee()));
        }
        return feeDetails;
    }

    private static Map<String, BigDecimal> separateCompensation(List<InternalAccountFees> fees) {
        Map<String, BigDecimal> feeDetails = new HashMap<>();
        for (InternalAccountFees fe : fees) {
            BigDecimal total = feeDetails.get(fe.getInternalAccountRef() + ":" + fe.getWrapper() + ":" + fe.getFundUuid());
            if (total == null) {
                feeDetails.put(fe.getInternalAccountRef() + ":" + fe.getWrapper() + ":" + fe.getFundUuid(), fe.getCompensationDue());
                continue;
            }
            feeDetails.put(fe.getInternalAccountRef() + ":" + fe.getWrapper() + ":" + fe.getFundUuid(), total.add(fe.getCompensationDue()));
        }
        return feeDetails;
    }

    @Override
    public void run(String... args) throws JSchException, SQLException, IOException, ParseException, InterruptedException {
        // Public key authentication (pointing to private key and including passphrase). It can be done with password authentication as well
        JSch jsch = new JSch();
        jsch.addIdentity(privateKeyPath, passphrase);
        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no"); // alternatively: try to 'ssh' from the command line and accept the public key (the host will be added to '~/.ssh/known_hosts')
        // Connect to SSH jump server (this does not show an authentication code)
        Session session = jsch.getSession(sshUsername, sshHost, sshPort);
        session.setConfig(config);
        session.connect();
        // Forward randomly chosen local port through the SSH channel to database host/port
        int forwardedPort = session.setPortForwardingL(0, dbHost, dbPort);
        // Connect to the forwarded port (the local end of the SSH tunnel)
        String url = "jdbc:mysql://localhost:" + forwardedPort + databaseName;
        try (Connection conn = DriverManager.getConnection(url, dbUsername, dbPassword)) {

            // Parse CSV file
            parseCustomerCSVFile();

            // Map customerIDs to internalAcocuntIDs
            mapCustomerIdsToInternalAccountIDs(conn);

            populateFundStatus(conn);

            log.info(String.format("%d customerIDs, %d internal account IDs", totalCustomerIDs, totalInternalAccountRefs));

            // Perform query for each internalAccountIDs
            queryAccountPostingForFees(conn);

            // Generate report
            generateCSVReport();

            generatePostingsSQL(conn, "/Users/nicholas.wright/Downloads/V20240529_001__Upload_EXG_Cash_journal_Promotional_Fee_Remediation.sql", "Missed Referral Rewards - ", Main::separateFees);
            generatePostingsSQL(conn, "/Users/nicholas.wright/Downloads/V20240529_002__Upload_EXG_Cash_journal_Promotional_Fee_Compensation.sql", "Missed Referral Rewards Comp - ", Main::separateCompensation);

            // Generate SQL for postings
            generateCompCashJournal();
        } finally {
            session.disconnect();
        }
    }

    private boolean isClosingClosedSaved(String customerId) {
        List<FundStatus> statuses = customerIdToFundStatus.get(customerId);
        Optional<FundStatus> closedClosingStatus = statuses.stream().filter(a -> a.getStatus().equals("CLOSING") || a.getStatus().equals("CLOSED") || a.getStatus().equals("SAVED")).findFirst();
        return closedClosingStatus.isPresent();
    }

    private void generateCompCashJournal() {

    }

    private boolean isInternalAccountClosing(List<FundStatus> statuses, String internalAccount) {
        for( FundStatus fs : statuses) {
            if( fs.getInternalAccountRef().equals(internalAccount) && fs.isClosingOrClosedOrSaved()) {
                return true;
            }
        }
        return false;
    }
}

@Getter
@Setter
@Builder
class InternalAccountFees {
    private String internalAccountRef;
    private String fundUuid;
    private String wrapper;
    private String date;
    private String type;
    private BigDecimal fee;
    private BigDecimal compensationDue;
}

@Getter
@Builder
@ToString
class FundStatus {
    private String internalAccountRef;
    private String type;
    private String status;
    private String goalType;

    public boolean isClosingOrClosedOrSaved() {
        return getStatus().equals("CLOSING") || getStatus().equals("CLOSED") || getStatus().equals("SAVED") || getStatus().equals("DRAFT");
    }
}