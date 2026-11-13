package org.jahia.community.maintenancepersite.filter;

import org.jahia.api.Constants;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.Resource;
import org.jahia.services.render.filter.RenderChain;
import org.jahia.services.render.filter.cache.ClientCachePolicy;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.jcr.AccessDeniedException;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletResponse;

import org.jahia.services.render.RenderException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MaintenancePerSiteFilterTest {

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
        when(site.hasProperty(MaintenancePerSiteFilter.PROP_MAINTENANCE_PAGE)).thenReturn(true);
        when(site.getProperty(MaintenancePerSiteFilter.PROP_MAINTENANCE_PAGE)).thenReturn(maintenanceProperty);
        when(maintenanceProperty.getNode()).thenReturn(maintenancePage);
        when(maintenancePage.getPath()).thenReturn(MAINTENANCE_PATH);
    }

    @Test
    public void prepare_liveSiteInMaintenance_servesMaintenancePageWith503() throws Exception {
        wireLiveSiteWithMixin(true);
        wireResolvedMaintenancePage();
        when(resource.getNodePath()).thenReturn(OTHER_PATH);
        when(renderContext.getResponse()).thenReturn(response);
        doReturn(MAINTENANCE_HTML).when(filter).renderMaintenancePage(maintenancePage, renderContext, resource);

        String result = filter.prepare(renderContext, resource, chain);

        assertEquals(MAINTENANCE_HTML, result);
        verify(response).setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        verify(response).setHeader("Retry-After", String.valueOf(MaintenancePerSiteFilter.RETRY_AFTER_SECONDS));
        verify(response).setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        verify(response).setHeader("Pragma", "no-cache");
        // Jahia's client-cache-control rewrites Cache-Control from the render context policy, so the
        // module must raise that policy too: PRIVATE maps to the "private, no-cache, no-store, ..." template.
        verify(renderContext).computeClientCachePolicy(ClientCachePolicy.PRIVATE);
    }

    @Test
    public void prepare_maintenancePageRenderFails_failsOpen() throws Exception {
        wireLiveSiteWithMixin(true);
        wireResolvedMaintenancePage();
        when(resource.getNodePath()).thenReturn(OTHER_PATH);
        doThrow(new RenderException("no view")).when(filter)
                .renderMaintenancePage(maintenancePage, renderContext, resource);

        String result = filter.prepare(renderContext, resource, chain);

        assertNull(result);
        verify(renderContext, never()).getResponse();
        verify(response, never()).setStatus(anyInt());
        verify(renderContext, never()).computeClientCachePolicy(any());
    }

    @Test
    public void prepare_responseAlreadyCommitted_servesBodyWithout503() throws Exception {
        wireLiveSiteWithMixin(true);
        wireResolvedMaintenancePage();
        when(resource.getNodePath()).thenReturn(OTHER_PATH);
        when(renderContext.getResponse()).thenReturn(response);
        when(response.isCommitted()).thenReturn(true);
        doReturn(MAINTENANCE_HTML).when(filter).renderMaintenancePage(maintenancePage, renderContext, resource);

        String result = filter.prepare(renderContext, resource, chain);

        assertEquals(MAINTENANCE_HTML, result);
        verify(response, never()).setStatus(anyInt());
        verify(renderContext).computeClientCachePolicy(ClientCachePolicy.PRIVATE);
    }

    @Test
    public void prepare_requestForMaintenancePageItself_returnsOutputNo503() throws Exception {
        wireLiveSiteWithMixin(true);
        wireResolvedMaintenancePage();
        when(resource.getNodePath()).thenReturn(MAINTENANCE_PATH);

        String result = filter.prepare(renderContext, resource, chain);

        assertNull(result);
        verify(filter, never()).renderMaintenancePage(any(), any(), any());
        verify(response, never()).setStatus(anyInt());
        verify(renderContext, never()).computeClientCachePolicy(any());
    }

    @Test
    public void prepare_siteResolutionThrowsRepositoryException_failsOpen() throws Exception {
        when(renderContext.getWorkspace()).thenReturn(Constants.LIVE_WORKSPACE);
        when(resource.getNode()).thenReturn(resourceNode);
        when(resourceNode.getResolveSite()).thenThrow(new RepositoryException("repo down"));

        String result = filter.prepare(renderContext, resource, chain);

        assertNull(result);
        verify(filter, never()).renderMaintenancePage(any(), any(), any());
        verify(response, never()).setStatus(anyInt());
        verify(renderContext, never()).computeClientCachePolicy(any());
    }

    @Test
    public void prepare_siteWithoutMixin_returnsOutput() throws Exception {
        wireLiveSiteWithMixin(false);

        String result = filter.prepare(renderContext, resource, chain);

        assertNull(result);
        verify(filter, never()).renderMaintenancePage(any(), any(), any());
        verify(response, never()).setStatus(anyInt());
        verify(renderContext, never()).computeClientCachePolicy(any());
    }

    @Test
    public void prepare_nonLiveWorkspace_returnsOutput() throws Exception {
        when(renderContext.getWorkspace()).thenReturn(Constants.EDIT_WORKSPACE);

        String result = filter.prepare(renderContext, resource, chain);

        assertNull(result);
        verify(filter, never()).renderMaintenancePage(any(), any(), any());
        verify(response, never()).setStatus(anyInt());
        verify(renderContext, never()).computeClientCachePolicy(any());
    }

    @Test
    public void prepare_mixinButMaintenancePagePropertyNotSet_failsOpen() throws Exception {
        wireLiveSiteWithMixin(true);
        when(site.hasProperty(MaintenancePerSiteFilter.PROP_MAINTENANCE_PAGE)).thenReturn(false);

        String result = filter.prepare(renderContext, resource, chain);

        assertNull(result);
        verify(filter, never()).renderMaintenancePage(any(), any(), any());
        verify(response, never()).setStatus(anyInt());
        verify(renderContext, never()).computeClientCachePolicy(any());
    }

    @Test
    public void prepare_mixinButMaintenancePageReferenceUnresolved_failsOpen() throws Exception {
        wireLiveSiteWithMixin(true);
        when(site.hasProperty(MaintenancePerSiteFilter.PROP_MAINTENANCE_PAGE)).thenReturn(true);
        when(site.getProperty(MaintenancePerSiteFilter.PROP_MAINTENANCE_PAGE)).thenThrow(new PathNotFoundException("gone"));

        String result = filter.prepare(renderContext, resource, chain);

        assertNull(result);
        verify(filter, never()).renderMaintenancePage(any(), any(), any());
        verify(response, never()).setStatus(anyInt());
        verify(renderContext, never()).computeClientCachePolicy(any());
    }

    @Test
    public void prepare_mixinButRepositoryErrorResolvingPage_failsOpen() throws Exception {
        wireLiveSiteWithMixin(true);
        when(site.hasProperty(MaintenancePerSiteFilter.PROP_MAINTENANCE_PAGE)).thenReturn(true);
        when(site.getProperty(MaintenancePerSiteFilter.PROP_MAINTENANCE_PAGE)).thenThrow(new AccessDeniedException("acl"));

        String result = filter.prepare(renderContext, resource, chain);

        assertNull(result);
        verify(filter, never()).renderMaintenancePage(any(), any(), any());
        verify(response, never()).setStatus(anyInt());
        verify(renderContext, never()).computeClientCachePolicy(any());
    }

    @Test
    public void prepare_mixinButMaintenancePageNodeNull_failsOpen() throws Exception {
        wireLiveSiteWithMixin(true);
        when(site.hasProperty(MaintenancePerSiteFilter.PROP_MAINTENANCE_PAGE)).thenReturn(true);
        when(site.getProperty(MaintenancePerSiteFilter.PROP_MAINTENANCE_PAGE)).thenReturn(maintenanceProperty);
        when(maintenanceProperty.getNode()).thenReturn(null);

        String result = filter.prepare(renderContext, resource, chain);

        assertNull(result);
        verify(filter, never()).renderMaintenancePage(any(), any(), any());
        verify(response, never()).setStatus(anyInt());
        verify(renderContext, never()).computeClientCachePolicy(any());
    }

    @Test
    public void renderMaintenancePage_rendersItAsMainResourceThenRestoresPrevious() throws Exception {
        Resource previousMain = resource;
        when(renderContext.getMainResource()).thenReturn(previousMain);
        when(resource.getTemplateType()).thenReturn("html");
        doReturn(MAINTENANCE_HTML).when(filter).renderResource(any(Resource.class), any(RenderContext.class));

        String result = filter.renderMaintenancePage(maintenancePage, renderContext, resource);

        // AggregateCacheFilter only resolves cache:include placeholders for the main resource: rendered
        // as a sub-resource, the 503 body would be an empty <!-- cache:include --> tag.
        assertEquals(MAINTENANCE_HTML, result);
        InOrder order = inOrder(renderContext, filter);
        order.verify(renderContext).setMainResource(argThat(r -> r != null && r.getNode() == maintenancePage));
        order.verify(filter).renderResource(argThat(r -> r.getNode() == maintenancePage), any(RenderContext.class));
        order.verify(renderContext).setMainResource(previousMain);
    }

    @Test
    public void prepare_maintenancePageRendersNull_failsOpen() throws Exception {
        wireLiveSiteWithMixin(true);
        wireResolvedMaintenancePage();
        when(resource.getNodePath()).thenReturn(OTHER_PATH);
        doReturn(null).when(filter).renderMaintenancePage(maintenancePage, renderContext, resource);

        String result = filter.prepare(renderContext, resource, chain);

        // null lets the chain render the requested page normally: it must not get a 503 status.
        assertNull(result);
        verify(renderContext, never()).getResponse();
        verify(renderContext, never()).computeClientCachePolicy(any());
    }

    @Test
    public void execute_passesThePreparedMaintenanceBodyThrough() throws Exception {
        // When prepare() returns the maintenance body, RenderChain calls execute() on this filter with it.
        String result = filter.execute(MAINTENANCE_HTML, renderContext, resource, chain);

        assertEquals(MAINTENANCE_HTML, result);
        verify(filter, never()).renderMaintenancePage(any(), any(), any());
    }

    @Test
    public void renderMaintenancePage_restoresPreviousMainResourceWhenRenderFails() throws Exception {
        Resource previousMain = resource;
        when(renderContext.getMainResource()).thenReturn(previousMain);
        when(resource.getTemplateType()).thenReturn("html");
        doThrow(new RenderException("no view")).when(filter).renderResource(any(Resource.class), any(RenderContext.class));

        try {
            filter.renderMaintenancePage(maintenancePage, renderContext, resource);
            fail("RenderException expected");
        } catch (RenderException expected) {
            // the caller fails open on this exception
        }

        verify(renderContext).setMainResource(previousMain);
    }

    @Test
    public void activate_setsExpectedPriority() {
        filter.activate();

        assertEquals(3f, filter.getPriority(), 0.0f);
    }
}
