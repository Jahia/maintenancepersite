package org.jahia.community.maintenancepersite.filter;

import org.jahia.api.Constants;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.Resource;
import org.jahia.services.render.filter.RenderChain;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.servlet.http.HttpServletResponse;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MaintenancePerSiteFilterTest {

    private static final String PREVIOUS_OUT = "normal-output";
    private static final String MAINTENANCE_PATH = "/sites/mySite/home/maintenance";
    private static final String MAINTENANCE_URL = "/cms/render/live/en/sites/mySite/home/maintenance.html";

    @Mock
    private RenderContext renderContext;
    @Mock
    private Resource resource;
    @Mock
    private RenderChain chain;
    @Mock
    private JCRNodeWrapper resourceNode;
    @Mock
    private JCRSiteNode site;
    @Mock
    private JCRNodeWrapper maintenancePage;
    @Mock
    private JCRPropertyWrapper maintenanceProperty;
    @Mock
    private HttpServletResponse response;

    private MaintenancePerSiteFilter filter;
    private AutoCloseable mocks;

    @Before
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        filter = new MaintenancePerSiteFilter();
    }

    @Test
    public void execute_liveSiteWithMixinAndOtherResource_redirectsAndReturnsEmpty() throws Exception {
        when(renderContext.getWorkspace()).thenReturn(Constants.LIVE_WORKSPACE);
        when(resource.getNode()).thenReturn(resourceNode);
        when(resourceNode.getResolveSite()).thenReturn(site);
        when(site.isNodeType("jmix:maintenancePerSite")).thenReturn(true);
        when(site.getProperty("maintenancePage")).thenReturn(maintenanceProperty);
        when(maintenanceProperty.getNode()).thenReturn(maintenancePage);
        when(maintenancePage.getPath()).thenReturn(MAINTENANCE_PATH);
        when(maintenancePage.getUrl()).thenReturn(MAINTENANCE_URL);
        when(resource.getNodePath()).thenReturn("/sites/mySite/home");
        when(renderContext.getResponse()).thenReturn(response);

        String result = filter.execute(PREVIOUS_OUT, renderContext, resource, chain);

        assertEquals("", result);
        verify(response).sendRedirect(MAINTENANCE_URL);
    }

    @Test
    public void execute_liveSiteWithMixinOnMaintenanceResource_noRedirectReturnsOutput() throws Exception {
        when(renderContext.getWorkspace()).thenReturn(Constants.LIVE_WORKSPACE);
        when(resource.getNode()).thenReturn(resourceNode);
        when(resourceNode.getResolveSite()).thenReturn(site);
        when(site.isNodeType("jmix:maintenancePerSite")).thenReturn(true);
        when(site.getProperty("maintenancePage")).thenReturn(maintenanceProperty);
        when(maintenanceProperty.getNode()).thenReturn(maintenancePage);
        when(maintenancePage.getPath()).thenReturn(MAINTENANCE_PATH);
        when(resource.getNodePath()).thenReturn(MAINTENANCE_PATH);

        String result = filter.execute(PREVIOUS_OUT, renderContext, resource, chain);

        assertEquals(PREVIOUS_OUT, result);
        verify(response, never()).sendRedirect(MAINTENANCE_URL);
    }

    @Test
    public void execute_siteWithoutMixin_noRedirectReturnsOutput() throws Exception {
        when(renderContext.getWorkspace()).thenReturn(Constants.LIVE_WORKSPACE);
        when(resource.getNode()).thenReturn(resourceNode);
        when(resourceNode.getResolveSite()).thenReturn(site);
        when(site.isNodeType("jmix:maintenancePerSite")).thenReturn(false);

        String result = filter.execute(PREVIOUS_OUT, renderContext, resource, chain);

        assertEquals(PREVIOUS_OUT, result);
        verify(response, never()).sendRedirect(MAINTENANCE_URL);
    }

    @Test
    public void execute_nonLiveWorkspace_noRedirectReturnsOutput() throws Exception {
        when(renderContext.getWorkspace()).thenReturn(Constants.EDIT_WORKSPACE);

        String result = filter.execute(PREVIOUS_OUT, renderContext, resource, chain);

        assertEquals(PREVIOUS_OUT, result);
        verify(response, never()).sendRedirect(MAINTENANCE_URL);
    }

    @After
    public void tearDown() throws Exception {
        mocks.close();
    }
}
