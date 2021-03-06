package au.com.codeka.warworlds.server.handlers;

import au.com.codeka.common.protobuf.Messages;
import au.com.codeka.warworlds.server.RequestException;
import au.com.codeka.warworlds.server.RequestHandler;
import au.com.codeka.warworlds.server.data.DB;
import au.com.codeka.warworlds.server.data.SqlResult;
import au.com.codeka.warworlds.server.data.SqlStmt;

public class EmpiresCashAuditHandler extends RequestHandler {
    @Override
    protected void get() throws RequestException {
        int empireID = Integer.parseInt(getUrlParameter("empireid"));
        if (!getSession().isAdmin()) {
            throw new RequestException(403); // TODO: allow you to get your own...
        }

        String sql = "SELECT reason FROM empire_cash_audit WHERE empire_id = ? ORDER BY time DESC";
        try (SqlStmt stmt = DB.prepare(sql)) {
            stmt.setInt(1, empireID);
            SqlResult res = stmt.select();

            Messages.CashAuditRecords.Builder cash_audit_records_pb = Messages.CashAuditRecords.newBuilder();
            while (res.next()) {
                cash_audit_records_pb.addRecords(Messages.CashAuditRecord.parseFrom(res.getBytes(1)));
            }
            setResponseBody(cash_audit_records_pb.build());
        } catch(Exception e) {
            throw new RequestException(e);
        }
    }
}
