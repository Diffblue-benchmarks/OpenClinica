/*
 * OpenClinica is distributed under the
 * GNU Lesser General Public License (GNU LGPL).

 * For details see: http://www.openclinica.org/license
 * copyright 2003-2005 Akaza Research
 */
package org.akaza.openclinica.control.admin;

import java.io.File;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;

import core.org.akaza.openclinica.bean.admin.CRFBean;
import core.org.akaza.openclinica.bean.core.Role;
import core.org.akaza.openclinica.bean.submit.FormLayoutBean;
import org.akaza.openclinica.control.core.SecureController;
import org.akaza.openclinica.control.form.FormProcessor;
import core.org.akaza.openclinica.dao.admin.CRFDAO;
import core.org.akaza.openclinica.dao.submit.FormLayoutDAO;
import core.org.akaza.openclinica.i18n.core.LocaleResolver;
import org.akaza.openclinica.view.Page;
import core.org.akaza.openclinica.web.InsufficientPermissionException;
import core.org.akaza.openclinica.web.SQLInitServlet;
import core.org.akaza.openclinica.web.bean.EntityBeanTable;
import core.org.akaza.openclinica.web.bean.ListCRFRow;

/**
 * Lists all the CRF and their CRF versions
 *
 * @author jxu
 */
public class ListCRFServlet extends SecureController {
    Locale locale;

    // < ResourceBundle resexception,respage,resword,restext,resworkflow;
    /**
     *
     */
    @Override
    public void mayProceed() throws InsufficientPermissionException {

        locale = LocaleResolver.getLocale(request);
        // < resword =
        // ResourceBundle.getBundle("core.org.akaza.openclinica.i18n.words",locale);
        // < restext =
        // ResourceBundle.getBundle("core.org.akaza.openclinica.i18n.notes",locale);
        // < resworkflow =
        // ResourceBundle.getBundle("core.org.akaza.openclinica.i18n.workflow",locale);
        // <
        // resexception=ResourceBundle.getBundle("core.org.akaza.openclinica.i18n.exceptions",locale);
        // < respage =
        // ResourceBundle.getBundle("core.org.akaza.openclinica.i18n.page_messages",locale);

        if (ub.isSysAdmin() || ub.isTechAdmin()) {
            return;
        }

        if (currentRole.getRole().equals(Role.STUDYDIRECTOR) || currentRole.getRole().equals(Role.COORDINATOR)) {
            return;
        }

        addPageMessage(respage.getString("no_have_correct_privilege_current_study") + respage.getString("change_study_contact_sysadmin"));
        throw new InsufficientPermissionException(Page.MANAGE_STUDY_SERVLET, resexception.getString("not_study_director"), "1");

    }

    /**
     * Finds all the crfs
     *
     */
    @Override
    public void processRequest() throws Exception {
        if (currentStudy.getParentStudyId() > 0) {
            addPageMessage(respage.getString("no_crf_available_study_is_a_site"));
            forwardPage(Page.MENU_SERVLET);
            return;
        }

        session.removeAttribute("version");
        FormProcessor fp = new FormProcessor(request);
        // checks which module the requests are from
        String module = fp.getString(MODULE);

        if (module.equalsIgnoreCase("admin") && !(ub.isSysAdmin() || ub.isTechAdmin())) {
            addPageMessage(respage.getString("no_have_correct_privilege_current_study") + " " + respage.getString("change_active_study_or_contact"));
            forwardPage(Page.MENU_SERVLET);
            return;
        }
        request.setAttribute(MODULE, module);

        // if coming from change crf version -> display message
        String crfVersionChangeMsg = fp.getString("isFromCRFVersionBatchChange");
        if (crfVersionChangeMsg != null && !crfVersionChangeMsg.equals("")) {
            addPageMessage(crfVersionChangeMsg);
        }

        String dir = SQLInitServlet.getField("filePath") + "crf" + File.separator + "new" + File.separator;// for
        // crf
        // version
        // spreadsheet
        logger.debug("found directory: " + dir);

        CRFDAO cdao = new CRFDAO(sm.getDataSource());
        FormLayoutDAO fldao = new FormLayoutDAO(sm.getDataSource());
        ArrayList crfs = (ArrayList) cdao.findAll();
        for (int i = 0; i < crfs.size(); i++) {
            CRFBean eb = (CRFBean) crfs.get(i);
            logger.debug("crf id:" + eb.getId());
            ArrayList versions = (ArrayList) fldao.findAllByCRF(eb.getId());

            // check whether the speadsheet is available on the server
            for (int j = 0; j < versions.size(); j++) {
                FormLayoutBean cv = (FormLayoutBean) versions.get(j);
                File file = new File(dir + eb.getId() + cv.getOid() + ".xls");
                logger.debug("looking in " + dir + eb.getId() + cv.getOid() + ".xls");
                if (file.exists()) {
                    cv.setDownloadable(true);
                } else {
                    File file2 = new File(dir + eb.getId() + cv.getName() + ".xls");
                    logger.debug("initial failed, looking in " + dir + eb.getId() + cv.getName() + ".xls");
                    if (file2.exists()) {
                        cv.setDownloadable(true);
                    }
                }
            }
            eb.setVersions(versions);

        }

        EntityBeanTable table = fp.getEntityBeanTable();
        ArrayList allRows = ListCRFRow.generateRowsFromBeans(crfs);

        String[] columns = { resword.getString("CRF_name"), resword.getString("date_updated"), resword.getString("last_updated_by"),
                resword.getString("crf_oid"), resword.getString("versions"), resword.getString("date_created"), resword.getString("owner"),
                resword.getString("status"), resword.getString("download"), resword.getString("actions") };

        table.setColumns(new ArrayList(Arrays.asList(columns)));
        table.hideColumnLink(3);
        table.hideColumnLink(4); // oid column
        // BWP 3281: make the "owner" column sortable; table.hideColumnLink(7);
        table.hideColumnLink(8);
        table.setQuery("ListCRF", new HashMap());
        // YW << add "Enterprise CRF Catalog" link
        String crfCatalogField = "crfCatalog";
        // table.addLink(resword.getString("openclinica_CRF_catalog"),
        // SQLInitServlet.getEnterpriseField(crfCatalogField));
        // YW >>
        // TODO add i18n links to the above, tbh
        table.setRows(allRows);
        table.computeDisplay();

        request.setAttribute("table", table);
        request.setAttribute("study", currentStudy);
        request.setAttribute("originatingPage", URLEncoder.encode("ListCRF?module=" + module, "UTF-8"));

        resetPanel();
        panel.setStudyInfoShown(false);
        panel.setOrderedData(true);
        panel.setSubmitDataModule(false);
        panel.setExtractData(false);
        panel.setCreateDataset(false);

        forwardPage(Page.CRF_LIST);
    }

    @Override
    protected String getAdminServlet() {
        if (ub.isSysAdmin()) {
            return SecureController.ADMIN_SERVLET_CODE;
        } else {
            return "";
        }
    }

}