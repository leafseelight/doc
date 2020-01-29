//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package org.elasticsearch.client.transport;

import java.io.Closeable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.lucene.util.IOUtils;
import org.elasticsearch.action.Action;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionModule;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionRequestBuilder;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.action.GenericAction;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.client.support.AbstractClient;
import org.elasticsearch.cluster.ClusterModule;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.UUIDs;
import org.elasticsearch.common.component.LifecycleComponent;
import org.elasticsearch.common.inject.Injector;
import org.elasticsearch.common.inject.Module;
import org.elasticsearch.common.inject.ModulesBuilder;
import org.elasticsearch.common.io.stream.NamedWriteableRegistry;
import org.elasticsearch.common.io.stream.NamedWriteableRegistry.Entry;
import org.elasticsearch.common.network.NetworkModule;
import org.elasticsearch.common.network.NetworkService;
import org.elasticsearch.common.settings.ClusterSettings;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.SettingsModule;
import org.elasticsearch.common.settings.Setting.Property;
import org.elasticsearch.common.settings.Settings.Builder;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.util.BigArrays;
import org.elasticsearch.common.util.PageCacheRecycler;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.http.HttpServerTransport.Dispatcher;
import org.elasticsearch.indices.breaker.CircuitBreakerService;
import org.elasticsearch.node.InternalSettingsPreparer;
import org.elasticsearch.node.Node;
import org.elasticsearch.plugins.ActionPlugin;
import org.elasticsearch.plugins.NetworkPlugin;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.plugins.PluginsService;
import org.elasticsearch.plugins.SearchPlugin;
import org.elasticsearch.plugins.ActionPlugin.ActionHandler;
import org.elasticsearch.search.SearchModule;
import org.elasticsearch.threadpool.ExecutorBuilder;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TcpTransport;
import org.elasticsearch.transport.Transport;
import org.elasticsearch.transport.TransportService;
import org.elasticsearch.usage.UsageService;

public abstract class TransportClient extends AbstractClient {
    public static final Setting<TimeValue> CLIENT_TRANSPORT_NODES_SAMPLER_INTERVAL;
    public static final Setting<TimeValue> CLIENT_TRANSPORT_PING_TIMEOUT;
    public static final Setting<Boolean> CLIENT_TRANSPORT_IGNORE_CLUSTER_NAME;
    public static final Setting<Boolean> CLIENT_TRANSPORT_SNIFF;
    public static final String CLIENT_TYPE = "transport";
    final Injector injector;
    protected final NamedWriteableRegistry namedWriteableRegistry;
    private final List<LifecycleComponent> pluginLifecycleComponents;
    private final TransportClientNodesService nodesService;
    private final TransportProxyClient proxy;

    private static PluginsService newPluginService(Settings settings, Collection<Class<? extends Plugin>> plugins) {
        Builder settingsBuilder = Settings.builder().put(TcpTransport.PING_SCHEDULE.getKey(), "5s").put(InternalSettingsPreparer.prepareSettings(settings)).put(NetworkService.NETWORK_SERVER.getKey(), false).put(CLIENT_TYPE_SETTING_S.getKey(), "transport");
        return new PluginsService(settingsBuilder.build(), (Path)null, (Path)null, (Path)null, plugins);
    }

    protected static Collection<Class<? extends Plugin>> addPlugins(Collection<Class<? extends Plugin>> collection, Class... plugins) {
        return addPlugins(collection, (Collection)Arrays.asList(plugins));
    }

    protected static Collection<Class<? extends Plugin>> addPlugins(Collection<Class<? extends Plugin>> collection, Collection<Class<? extends Plugin>> plugins) {
        ArrayList<Class<? extends Plugin>> list = new ArrayList(collection);
        Iterator var3 = plugins.iterator();

        while(var3.hasNext()) {
            Class<? extends Plugin> p = (Class)var3.next();
            if(list.contains(p)) {
                throw new IllegalArgumentException("plugin already exists: " + p);
            }

            list.add(p);
        }

        return list;
    }

    private static TransportClient.ClientTemplate buildTemplate(Settings providedSettings, Settings defaultSettings, Collection<Class<? extends Plugin>> plugins, TransportClient.HostFailureListener failureListner) {
        if(!Node.NODE_NAME_SETTING.exists(providedSettings)) {
            providedSettings = Settings.builder().put(providedSettings).put(Node.NODE_NAME_SETTING.getKey(), "_client_").build();
        }

        PluginsService pluginsService = newPluginService(providedSettings, plugins);
        Settings settings = Settings.builder().put(defaultSettings).put(pluginsService.updatedSettings()).build();
        List<Closeable> resourcesToClose = new ArrayList();
        ThreadPool threadPool = new ThreadPool(settings, new ExecutorBuilder[0]);
        resourcesToClose.add(() -> {
            ThreadPool.terminate(threadPool, 10L, TimeUnit.SECONDS);
        });
        NetworkService networkService = new NetworkService(Collections.emptyList());

        try {
            List<Setting<?>> additionalSettings = new ArrayList(pluginsService.getPluginSettings());
            List<String> additionalSettingsFilter = new ArrayList(pluginsService.getPluginSettingsFilter());
            Iterator var11 = threadPool.builders().iterator();

            while(var11.hasNext()) {
                ExecutorBuilder<?> builder = (ExecutorBuilder)var11.next();
                additionalSettings.addAll(builder.getRegisteredSettings());
            }

            SettingsModule settingsModule = new SettingsModule(settings, additionalSettings, additionalSettingsFilter);
            SearchModule searchModule = new SearchModule(settings, true, pluginsService.filterPlugins(SearchPlugin.class));
            List<Entry> entries = new ArrayList();
            entries.addAll(NetworkModule.getNamedWriteables());
            entries.addAll(searchModule.getNamedWriteables());
            entries.addAll(ClusterModule.getNamedWriteables());
            entries.addAll((Collection)pluginsService.filterPlugins(Plugin.class).stream().flatMap((p) -> {
                return p.getNamedWriteables().stream();
            }).collect(Collectors.toList()));
            NamedWriteableRegistry namedWriteableRegistry = new NamedWriteableRegistry(entries);
            NamedXContentRegistry xContentRegistry = new NamedXContentRegistry((List)Stream.of(new Stream[]{searchModule.getNamedXContents().stream(), pluginsService.filterPlugins(Plugin.class).stream().flatMap((p) -> {
                return p.getNamedXContent().stream();
            })}).flatMap(Function.identity()).collect(Collectors.toList()));
            ModulesBuilder modules = new ModulesBuilder();
            Iterator var17 = pluginsService.createGuiceModules().iterator();

            while(var17.hasNext()) {
                Module pluginModule = (Module)var17.next();
                modules.add(new Module[]{pluginModule});
            }

            modules.add(new Module[]{(b) -> {
                b.bind(ThreadPool.class).toInstance(threadPool);
            }});
            ActionModule actionModule = new ActionModule(true, settings, (IndexNameExpressionResolver)null, settingsModule.getIndexScopedSettings(), settingsModule.getClusterSettings(), settingsModule.getSettingsFilter(), threadPool, pluginsService.filterPlugins(ActionPlugin.class), (NodeClient)null, (CircuitBreakerService)null, (UsageService)null);
            modules.add(new Module[]{actionModule});
            CircuitBreakerService circuitBreakerService = Node.createCircuitBreakerService(settingsModule.getSettings(), settingsModule.getClusterSettings());
            resourcesToClose.add(circuitBreakerService);
            PageCacheRecycler pageCacheRecycler = new PageCacheRecycler(settings);
            BigArrays bigArrays = new BigArrays(pageCacheRecycler, circuitBreakerService);
            resourcesToClose.add(bigArrays);
            modules.add(new Module[]{settingsModule});
            NetworkModule networkModule = new NetworkModule(settings, true, pluginsService.filterPlugins(NetworkPlugin.class), threadPool, bigArrays, pageCacheRecycler, circuitBreakerService, namedWriteableRegistry, xContentRegistry, networkService, (Dispatcher)null);
            Transport transport = (Transport)networkModule.getTransportSupplier().get();
            TransportService transportService = new TransportService(settings, transport, threadPool, networkModule.getTransportInterceptor(), (boundTransportAddress) -> {
                return DiscoveryNode.createLocal(settings, new TransportAddress(TransportAddress.META_ADDRESS, 0), UUIDs.randomBase64UUID());
            }, (ClusterSettings)null, Collections.emptySet());
            modules.add(new Module[]{(b) -> {
                b.bind(BigArrays.class).toInstance(bigArrays);
                b.bind(PluginsService.class).toInstance(pluginsService);
                b.bind(CircuitBreakerService.class).toInstance(circuitBreakerService);
                b.bind(NamedWriteableRegistry.class).toInstance(namedWriteableRegistry);
                b.bind(Transport.class).toInstance(transport);
                b.bind(TransportService.class).toInstance(transportService);
                b.bind(NetworkService.class).toInstance(networkService);
            }});
            Injector injector = modules.createInjector();
            TransportClientNodesService nodesService = new TransportClientNodesService(settings, transportService, threadPool, failureListner == null?(t, e) -> {
            }:failureListner);
            List<ActionPlugin> actionPlugins = pluginsService.filterPlugins(ActionPlugin.class);
            List<GenericAction> clientActions = (List)actionPlugins.stream().flatMap((p) -> {
                return p.getClientActions().stream();
            }).collect(Collectors.toList());
            List<? extends GenericAction<?, ?>> baseActions = (List)actionModule.getActions().values().stream().map(ActionHandler::getAction).collect(Collectors.toList());
            clientActions.addAll(baseActions);
            TransportProxyClient proxy = new TransportProxyClient(settings, transportService, nodesService, clientActions);
            Stream var10002 = pluginsService.getGuiceServiceClasses().stream();
            Objects.requireNonNull(injector);
            List<LifecycleComponent> pluginLifecycleComponents = new ArrayList((Collection)var10002.map(injector::getInstance).collect(Collectors.toList()));
            resourcesToClose.addAll(pluginLifecycleComponents);
            transportService.start();
            transportService.acceptIncomingRequests();
            TransportClient.ClientTemplate transportClient = new TransportClient.ClientTemplate(injector, pluginLifecycleComponents, nodesService, proxy, namedWriteableRegistry);
            resourcesToClose.clear();
            TransportClient.ClientTemplate var32 = transportClient;
            return var32;
        } finally {
            IOUtils.closeWhileHandlingException(resourcesToClose);
        }
    }

    public TransportClient(Settings settings, Collection<Class<? extends Plugin>> plugins) {
        this(buildTemplate(settings, Settings.EMPTY, plugins, (TransportClient.HostFailureListener)null));
    }

    protected TransportClient(Settings settings, Settings defaultSettings, Collection<Class<? extends Plugin>> plugins, TransportClient.HostFailureListener hostFailureListener) {
        this(buildTemplate(settings, defaultSettings, plugins, hostFailureListener));
    }

    private TransportClient(TransportClient.ClientTemplate template) {
        super(template.getSettings(), template.getThreadPool());
        this.injector = template.injector;
        this.pluginLifecycleComponents = Collections.unmodifiableList(template.pluginLifecycleComponents);
        this.nodesService = template.nodesService;
        this.proxy = template.proxy;
        this.namedWriteableRegistry = template.namedWriteableRegistry;
    }

    public List<TransportAddress> transportAddresses() {
        return this.nodesService.transportAddresses();
    }

    public List<DiscoveryNode> connectedNodes() {
        return this.nodesService.connectedNodes();
    }

    public List<DiscoveryNode> filteredNodes() {
        return this.nodesService.filteredNodes();
    }

    public List<DiscoveryNode> listedNodes() {
        return this.nodesService.listedNodes();
    }

    public TransportClient addTransportAddress(TransportAddress transportAddress) {
        this.nodesService.addTransportAddresses(new TransportAddress[]{transportAddress});
        return this;
    }

    public TransportClient addTransportAddresses(TransportAddress... transportAddress) {
        this.nodesService.addTransportAddresses(transportAddress);
        return this;
    }

    public TransportClient removeTransportAddress(TransportAddress transportAddress) {
        this.nodesService.removeTransportAddress(transportAddress);
        return this;
    }

    public void close() {
        List<Closeable> closeables = new ArrayList();
        closeables.add(this.nodesService);
        closeables.add((Closeable)this.injector.getInstance(TransportService.class));
        Iterator var2 = this.pluginLifecycleComponents.iterator();

        while(var2.hasNext()) {
            LifecycleComponent plugin = (LifecycleComponent)var2.next();
            closeables.add(plugin);
        }

        closeables.add(() -> {
            ThreadPool.terminate((ThreadPool)this.injector.getInstance(ThreadPool.class), 10L, TimeUnit.SECONDS);
        });
        closeables.add((Closeable)this.injector.getInstance(BigArrays.class));
        IOUtils.closeWhileHandlingException(closeables);
    }

    protected <Request extends ActionRequest, Response extends ActionResponse, RequestBuilder extends ActionRequestBuilder<Request, Response, RequestBuilder>> void doExecute(Action<Request, Response, RequestBuilder> action, Request request, ActionListener<Response> listener) {
        this.proxy.execute(action, request, listener);
    }

    TransportClientNodesService getNodesService() {
        return this.nodesService;
    }

    static {
        CLIENT_TRANSPORT_NODES_SAMPLER_INTERVAL = Setting.positiveTimeSetting("client.transport.nodes_sampler_interval", TimeValue.timeValueSeconds(5L), new Property[]{Property.NodeScope});
        CLIENT_TRANSPORT_PING_TIMEOUT = Setting.positiveTimeSetting("client.transport.ping_timeout", TimeValue.timeValueSeconds(5L), new Property[]{Property.NodeScope});
        CLIENT_TRANSPORT_IGNORE_CLUSTER_NAME = Setting.boolSetting("client.transport.ignore_cluster_name", false, new Property[]{Property.NodeScope});
        CLIENT_TRANSPORT_SNIFF = Setting.boolSetting("client.transport.sniff", false, new Property[]{Property.NodeScope});
    }

    @FunctionalInterface
    public interface HostFailureListener {
        void onNodeDisconnected(DiscoveryNode var1, Exception var2);
    }

    private static final class ClientTemplate {
        final Injector injector;
        private final List<LifecycleComponent> pluginLifecycleComponents;
        private final TransportClientNodesService nodesService;
        private final TransportProxyClient proxy;
        private final NamedWriteableRegistry namedWriteableRegistry;

        private ClientTemplate(Injector injector, List<LifecycleComponent> pluginLifecycleComponents, TransportClientNodesService nodesService, TransportProxyClient proxy, NamedWriteableRegistry namedWriteableRegistry) {
            this.injector = injector;
            this.pluginLifecycleComponents = pluginLifecycleComponents;
            this.nodesService = nodesService;
            this.proxy = proxy;
            this.namedWriteableRegistry = namedWriteableRegistry;
        }

        Settings getSettings() {
            return (Settings)this.injector.getInstance(Settings.class);
        }

        ThreadPool getThreadPool() {
            return (ThreadPool)this.injector.getInstance(ThreadPool.class);
        }
    }
}
