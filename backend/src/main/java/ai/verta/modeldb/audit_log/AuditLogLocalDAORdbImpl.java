package ai.verta.modeldb.audit_log;

import ai.verta.modeldb.entities.audit_log.AuditLogLocalEntity;
import ai.verta.modeldb.utils.ModelDBHibernateUtil;
import ai.verta.modeldb.utils.ModelDBUtils;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.Transaction;

public class AuditLogLocalDAORdbImpl implements AuditLogLocalDAO {

  private static final Logger LOGGER =
      LogManager.getLogger(AuditLogLocalDAORdbImpl.class.getName());

  public void saveAuditLogs(List<AuditLogLocalEntity> auditLogEntities) {
    try (Session session = ModelDBHibernateUtil.getSessionFactory().openSession()) {
      Transaction transaction = session.beginTransaction();
      saveAuditLogs(session, auditLogEntities);
      transaction.commit();
      LOGGER.debug("Audit logged successfully");
    } catch (Exception ex) {
      if (ModelDBUtils.needToRetry(ex)) {
        saveAuditLogs(auditLogEntities);
      } else {
        throw ex;
      }
    }
  }

  public void saveAuditLogs(Session session, List<AuditLogLocalEntity> auditLogEntities) {
    auditLogEntities.forEach(session::save);
  }
}
