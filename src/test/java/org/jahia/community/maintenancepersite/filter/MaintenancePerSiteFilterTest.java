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

import javax.jcr.PathNotFoundException;
import javax.servlet.http.HttpServletResponse;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MaintenancePerSiteFilterTest {

    private static final String PREVIOUS_OUT = "normal-output";
    private static final String MAINTENANCE_HTML = "<html><body>Under maintenance</body></html>";
    private static final String MAINTENANCE_PATH = "/sites/mySite/home/maintenance";
    private static final String OTHER_PATH = "/sites/mySite/home";
    private static final String SITE_NAME = "mySite";

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
        // Spy so the render seam can be stubbed without a live Jahia render engine.
        filter = spy(new MaintenancePerSiteFilter());
    }

    @After
    public void tearDown() throws Exception {
        mocks.close();
    }

    private void wireLiveSiteWithMixin(boolean hasMixin) throws Exception {
        when(renderContext.getWorkspace()).thenReturn(Constants.LIVE_WORKSPACE);
        when(resource.getNode()).thenReturn(resourceNode);
        when(resourceNode.getResolveSite()).thenReturn(site);
        when(site.isNodeType(MaintenancePerSiteFilter.MIXIN_MAINTENANCE)).thenReturn(hasMixin);
        when(site.getName()).thenReturn(SITE_NAME);
    }

    private void wireResolvedMaintenancePage() throws Exception {
        when(site.hasProperty("maintenancePage")).thenReturn(true);
        when(site.getProperty("maintenancePage")).thenReturn(maintenanceProperty);
        when(maintenanceProperty.getNode()).thenReturn(maintenancePage);
        when(maintenancePage.getPath()).thenReturn(MAINTENANCE_PATH);
    }

    @Test
    public void execute_liveSiteInMaintenance_servesMaintenancePageWith503() throws Exception {
        wireLiveSiteWithMixin(true);
        wireResolvedMaintenancePage();
        when(resource.getNodePath()).thenReturn(OTHER_PATH);
        when(renderContext.getResponse()).thenReturn(response);
        doReturn(MAINTENANCE_HTML).when(filter).renderMaintenancePage(maintenancePage, renderContext, resource);

        String result = filter.execute(PREVIOUS_OUT, renderContext, resource, chain);

        assertEquals(MAINTENANCE_HTML, result);
        verify(response).setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        verify(response).setHeader("Retry-After", "3600");
        verify(response).setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        verify(response).setHeader("Pragma", "no-cache");
    }

    @Test
    public void execute_requestForMaintenancePageItself_returnsOutputNo503() throws Exception {
        wireLiveSiteWithMixin(true);
        wireResolvedMaintenancePage();
        when(resource.getNodePath()).thenReturn(MAINTENANCE_PATH);

        String result = filter.execute(PREVIOUS_OUT, renderContext, resource, chain);

        assertEquals(PREVIOUS_OUT, result);
        verify(filter, never()).renderMaintenancePage(any(), any(), any());
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    public void execute_siteWithoutMixin_returnsOutput() throws Exception {
        wireLiveSiteWithMixin(false);

        String result = filter.execute(PREVIOUS_OUT, renderContext, resource, chain);

        assertEquals(PREVIOUS_OUT, result);
        verify(filter, never()).renderMaintenancePage(any(), any(), any());
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    public void execute_nonLiveWorkspace_returnsOutput() throws Exception {
        when(renderContext.getWorkspace()).thenReturn(Constants.EDIT_WORKSPACE);

        String result = filter.execute(PREVIOUS_OUT, renderContext, resource, chain);

        assertEquals(PREVIOUS_OUT, result);
        verify(filter, never()).renderMaintenancePage(any(), any(), any());
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    public void execute_mixinButMaintenancePagePropertyNotSet_failsOpen() throws Exception {
        wireLiveSiteWithMixin(true);
        when(site.hasProperty("maintenancePage")).thenReturn(false);

        String result = filter.execute(PREVIOUS_OUT, renderContext, resource, chain);

        assertEquals(PREVIOUS_OUT, result);
        verify(filter, never()).renderMaintenancePage(any(), any(), any());
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    public void execute_mixinButMaintenancePageReferenceUnresolved_failsOpen() throws Exception {
        wireLiveSiteWithMixin(true);
        when(site.hasProperty("maintenancePage")).thenReturn(true);
        when(site.getProperty("maintenancePage")).thenThrow(new PathNotFoundException("gone"));

        String result = filter.execute(PREVIOUS_OUT, renderContext, resource, chain);

        assertEquals(PREVIOUS_OUT, result);
        verify(filter, never()).renderMaintenancePage(any(), any(), any());
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    public void execute_mixinButMaintenancePageNodeNull_failsOpen() throws Exception {
        wireLiveSiteWithMixin(true);
        when(site.hasProperty("maintenancePage")).thenReturn(true);
        when(site.getProperty("maintenancePage")).thenReturn(maintenanceProperty);
        when(maintenanceProperty.getNode()).thenReturn(null);

        String result = filter.execute(PREVIOUS_OUT, renderContext, resource, chain);

        assertEquals(PREVIOUS_OUT, result);
        verify(filter, never()).renderMaintenancePage(any(), any(), any());
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    public void activate_setsExpectedPriority() {
        filter.activate();

        assertEquals(3f, filter.getPriority(), 0.0f);
    }
}
