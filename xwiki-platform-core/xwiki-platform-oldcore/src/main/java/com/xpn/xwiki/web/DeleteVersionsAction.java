/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package com.xpn.xwiki.web;

import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.suigeneris.jrcs.rcs.Version;
import org.xwiki.bridge.event.DocumentVersionRangeDeletedEvent;
import org.xwiki.component.annotation.Component;
import org.xwiki.refactoring.batch.BatchOperationExecutor;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.doc.XWikiDocumentArchive;

/**
 * Struts action for deleting document versions.
 *
 * @version $Id$
 */
@Component
@Named("deleteversions")
@Singleton
public class DeleteVersionsAction extends XWikiAction
{
    @Override
    protected Class<? extends XWikiForm> getFormClass()
    {
        return DeleteVersionsForm.class;
    }

    @Override
    public boolean action(XWikiContext context) throws XWikiException
    {
        DeleteVersionsForm form = (DeleteVersionsForm) context.getForm();
        if (!form.isConfirmed() || !csrfTokenCheck(context)) {
            return true;
        }

        XWikiDocument doc = context.getDoc();
        String language = form.getLanguage();
        XWikiDocument tdoc = doc.getTranslatedDocument(language, context);
        XWikiDocumentArchive archive = tdoc.getDocumentArchive(context);

        // Get the versions
        Version[] versions = getVersionsFromForm(form, archive);
        Version v1 = versions[0];
        Version v2 = versions[1];

        if (v1 != null && v2 != null) {
            // Find the lower and upper bounds
            Version upperBound = v1;
            Version lowerBound = v2;
            if (upperBound.compareVersions(lowerBound) < 0) {
                Version tmp = upperBound;
                upperBound = lowerBound;
                lowerBound = tmp;
            }

            // Remove the versions
            archive.removeVersions(upperBound, lowerBound, context);
            context.getWiki().getVersioningStore().saveXWikiDocArchive(archive, true, context);
            tdoc.setDocumentArchive(archive);

            // Is this the last remaining version? If so, then recycle the document.
            if (archive.getLatestVersion() == null) {
                // Wrap the work as a batch operation.
                BatchOperationExecutor batchOperationExecutor = Utils.getComponent(BatchOperationExecutor.class);
                batchOperationExecutor.execute(() -> {
                    if (StringUtils.isEmpty(language) || language.equals(doc.getDefaultLanguage())) {
                        context.getWiki().deleteAllDocuments(doc, context);
                    } else {
                        // Only delete the translation
                        context.getWiki().deleteDocument(tdoc, context);
                    }
                });
            } else {
                // There are still some versions left.
                // If we delete the most recent (current) version, then rollback to latest undeleted version.
                Version previousVersion = archive.getLatestVersion();
                if (!tdoc.getRCSVersion().equals(previousVersion)) {
                    context.getWiki().rollback(tdoc, previousVersion.toString(), false, context);
                }

                // Notify about versions delete
                this.observation.notify(new DocumentVersionRangeDeletedEvent(tdoc.getDocumentReferenceWithLocale(),
                    lowerBound.toString(), upperBound.toString()), tdoc, context);
            }
        }
        sendRedirect(context);
        return false;
    }

    /**
     * @param form the {@link DeleteVersionsForm} which to extract versions from
     * @param archive the document archive used to resolve pseudoversions, if needed
     * @return an array of versions to use as interval for deletion, regardless if "rev1" and "rev2" were passed
     *         individually or if just "rev" was used
     */
    private Version[] getVersionsFromForm(DeleteVersionsForm form, XWikiDocumentArchive archive)
    {
        // Determine if we used rev or rev1&rev2.
        String[] versions = new String[2];
        if (form.getRev() == null) {
            versions[0] = form.getRev1();
            versions[1] = form.getRev2();
        } else {
            versions[0] = form.getRev();
            versions[1] = form.getRev();
        }

        // Convert to Version objects.
        Version[] result = new Version[2];
        for (int i = 0; i < versions.length; i++) {
            // Support for the "latest" and "previous" pseudoversions.
            if ("latest".equals(versions[i])) {
                result[i] = archive.getLatestVersion();
            } else if ("previous".equals(versions[i])) {
                Version currentVersion = archive.getLatestVersion();
                result[i] = archive.getPrevVersion(currentVersion);
            } else {
                // Just use the given value.
                try {
                    result[i] = new Version(versions[i]);
                } catch (Exception e) {
                    // Protect against invalid versions.
                    result[i] = null;
                }
            }
        }

        return result;
    }

    /**
     * redirect back to view history.
     *
     * @param context used in redirecting
     * @throws XWikiException if any error
     */
    private void sendRedirect(XWikiContext context) throws XWikiException
    {
        // forward to view
        String redirect = Utils.getRedirect("view", "viewer=history", context);
        sendRedirect(context.getResponse(), redirect);
    }

    @Override
    public String render(XWikiContext context) throws XWikiException
    {
        return "deleteversionsconfirm";
    }
}
