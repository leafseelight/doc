package org.elasticsearch.client;

import org.elasticsearch.action.ActionFuture;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequestBuilder;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesResponse;
import org.elasticsearch.action.admin.indices.alias.exists.AliasesExistRequestBuilder;
import org.elasticsearch.action.admin.indices.alias.exists.AliasesExistResponse;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequestBuilder;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesResponse;
import org.elasticsearch.action.admin.indices.analyze.AnalyzeRequest;
import org.elasticsearch.action.admin.indices.analyze.AnalyzeRequestBuilder;
import org.elasticsearch.action.admin.indices.analyze.AnalyzeResponse;
import org.elasticsearch.action.admin.indices.cache.clear.ClearIndicesCacheRequest;
import org.elasticsearch.action.admin.indices.cache.clear.ClearIndicesCacheRequestBuilder;
import org.elasticsearch.action.admin.indices.cache.clear.ClearIndicesCacheResponse;
import org.elasticsearch.action.admin.indices.close.CloseIndexRequest;
import org.elasticsearch.action.admin.indices.close.CloseIndexRequestBuilder;
import org.elasticsearch.action.admin.indices.close.CloseIndexResponse;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequestBuilder;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequestBuilder;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexResponse;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequest;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequestBuilder;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.action.admin.indices.exists.types.TypesExistsRequest;
import org.elasticsearch.action.admin.indices.exists.types.TypesExistsRequestBuilder;
import org.elasticsearch.action.admin.indices.exists.types.TypesExistsResponse;
import org.elasticsearch.action.admin.indices.flush.FlushRequest;
import org.elasticsearch.action.admin.indices.flush.FlushRequestBuilder;
import org.elasticsearch.action.admin.indices.flush.FlushResponse;
import org.elasticsearch.action.admin.indices.flush.SyncedFlushRequest;
import org.elasticsearch.action.admin.indices.flush.SyncedFlushRequestBuilder;
import org.elasticsearch.action.admin.indices.flush.SyncedFlushResponse;
import org.elasticsearch.action.admin.indices.forcemerge.ForceMergeRequest;
import org.elasticsearch.action.admin.indices.forcemerge.ForceMergeRequestBuilder;
import org.elasticsearch.action.admin.indices.forcemerge.ForceMergeResponse;
import org.elasticsearch.action.admin.indices.get.GetIndexRequest;
import org.elasticsearch.action.admin.indices.get.GetIndexRequestBuilder;
import org.elasticsearch.action.admin.indices.get.GetIndexResponse;
import org.elasticsearch.action.admin.indices.mapping.get.GetFieldMappingsRequest;
import org.elasticsearch.action.admin.indices.mapping.get.GetFieldMappingsRequestBuilder;
import org.elasticsearch.action.admin.indices.mapping.get.GetFieldMappingsResponse;
import org.elasticsearch.action.admin.indices.mapping.get.GetMappingsRequest;
import org.elasticsearch.action.admin.indices.mapping.get.GetMappingsRequestBuilder;
import org.elasticsearch.action.admin.indices.mapping.get.GetMappingsResponse;
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingRequest;
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingRequestBuilder;
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingResponse;
import org.elasticsearch.action.admin.indices.open.OpenIndexRequest;
import org.elasticsearch.action.admin.indices.open.OpenIndexRequestBuilder;
import org.elasticsearch.action.admin.indices.open.OpenIndexResponse;
import org.elasticsearch.action.admin.indices.recovery.RecoveryRequest;
import org.elasticsearch.action.admin.indices.recovery.RecoveryRequestBuilder;
import org.elasticsearch.action.admin.indices.recovery.RecoveryResponse;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequestBuilder;
import org.elasticsearch.action.admin.indices.refresh.RefreshResponse;
import org.elasticsearch.action.admin.indices.rollover.RolloverRequest;
import org.elasticsearch.action.admin.indices.rollover.RolloverRequestBuilder;
import org.elasticsearch.action.admin.indices.rollover.RolloverResponse;
import org.elasticsearch.action.admin.indices.segments.IndicesSegmentResponse;
import org.elasticsearch.action.admin.indices.segments.IndicesSegmentsRequest;
import org.elasticsearch.action.admin.indices.segments.IndicesSegmentsRequestBuilder;
import org.elasticsearch.action.admin.indices.settings.get.GetSettingsRequest;
import org.elasticsearch.action.admin.indices.settings.get.GetSettingsRequestBuilder;
import org.elasticsearch.action.admin.indices.settings.get.GetSettingsResponse;
import org.elasticsearch.action.admin.indices.settings.put.UpdateSettingsRequest;
import org.elasticsearch.action.admin.indices.settings.put.UpdateSettingsRequestBuilder;
import org.elasticsearch.action.admin.indices.settings.put.UpdateSettingsResponse;
import org.elasticsearch.action.admin.indices.shards.IndicesShardStoreRequestBuilder;
import org.elasticsearch.action.admin.indices.shards.IndicesShardStoresRequest;
import org.elasticsearch.action.admin.indices.shards.IndicesShardStoresResponse;
import org.elasticsearch.action.admin.indices.shrink.ResizeRequest;
import org.elasticsearch.action.admin.indices.shrink.ResizeRequestBuilder;
import org.elasticsearch.action.admin.indices.shrink.ResizeResponse;
import org.elasticsearch.action.admin.indices.stats.IndicesStatsRequest;
import org.elasticsearch.action.admin.indices.stats.IndicesStatsRequestBuilder;
import org.elasticsearch.action.admin.indices.stats.IndicesStatsResponse;
import org.elasticsearch.action.admin.indices.template.delete.DeleteIndexTemplateRequest;
import org.elasticsearch.action.admin.indices.template.delete.DeleteIndexTemplateRequestBuilder;
import org.elasticsearch.action.admin.indices.template.delete.DeleteIndexTemplateResponse;
import org.elasticsearch.action.admin.indices.template.get.GetIndexTemplatesRequest;
import org.elasticsearch.action.admin.indices.template.get.GetIndexTemplatesRequestBuilder;
import org.elasticsearch.action.admin.indices.template.get.GetIndexTemplatesResponse;
import org.elasticsearch.action.admin.indices.template.put.PutIndexTemplateRequest;
import org.elasticsearch.action.admin.indices.template.put.PutIndexTemplateRequestBuilder;
import org.elasticsearch.action.admin.indices.template.put.PutIndexTemplateResponse;
import org.elasticsearch.action.admin.indices.upgrade.get.UpgradeStatusRequest;
import org.elasticsearch.action.admin.indices.upgrade.get.UpgradeStatusRequestBuilder;
import org.elasticsearch.action.admin.indices.upgrade.get.UpgradeStatusResponse;
import org.elasticsearch.action.admin.indices.upgrade.post.UpgradeRequest;
import org.elasticsearch.action.admin.indices.upgrade.post.UpgradeRequestBuilder;
import org.elasticsearch.action.admin.indices.upgrade.post.UpgradeResponse;
import org.elasticsearch.action.admin.indices.validate.query.ValidateQueryRequest;
import org.elasticsearch.action.admin.indices.validate.query.ValidateQueryRequestBuilder;
import org.elasticsearch.action.admin.indices.validate.query.ValidateQueryResponse;
import org.elasticsearch.common.Nullable;

public interface IndicesAdminClient extends ElasticsearchClient {
    ActionFutureIndicesExistsResponse exists(IndicesExistsRequest var1);

    void exists(IndicesExistsRequest var1, ActionListenerIndicesExistsResponse var2);

    IndicesExistsRequestBuilder prepareExists(String... var1);

    ActionFutureTypesExistsResponse typesExists(TypesExistsRequest var1);

    void typesExists(TypesExistsRequest var1, ActionListenerTypesExistsResponse var2);

    TypesExistsRequestBuilder prepareTypesExists(String... var1);

    ActionFutureIndicesStatsResponse stats(IndicesStatsRequest var1);

    void stats(IndicesStatsRequest var1, ActionListenerIndicesStatsResponse var2);

    IndicesStatsRequestBuilder prepareStats(String... var1);

    ActionFutureRecoveryResponse recoveries(RecoveryRequest var1);

    void recoveries(RecoveryRequest var1, ActionListenerRecoveryResponse var2);

    RecoveryRequestBuilder prepareRecoveries(String... var1);

    ActionFutureIndicesSegmentResponse segments(IndicesSegmentsRequest var1);

    void segments(IndicesSegmentsRequest var1, ActionListenerIndicesSegmentResponse var2);

    IndicesSegmentsRequestBuilder prepareSegments(String... var1);

    ActionFutureIndicesShardStoresResponse shardStores(IndicesShardStoresRequest var1);

    void shardStores(IndicesShardStoresRequest var1, ActionListenerIndicesShardStoresResponse var2);

    IndicesShardStoreRequestBuilder prepareShardStores(String... var1);

    ActionFutureCreateIndexResponse create(CreateIndexRequest var1);

    void create(CreateIndexRequest var1, ActionListenerCreateIndexResponse var2);

    CreateIndexRequestBuilder prepareCreate(String var1);

    ActionFutureDeleteIndexResponse delete(DeleteIndexRequest var1);

    void delete(DeleteIndexRequest var1, ActionListenerDeleteIndexResponse var2);

    DeleteIndexRequestBuilder prepareDelete(String... var1);

    ActionFutureCloseIndexResponse close(CloseIndexRequest var1);

    void close(CloseIndexRequest var1, ActionListenerCloseIndexResponse var2);

    CloseIndexRequestBuilder prepareClose(String... var1);

    ActionFutureOpenIndexResponse open(OpenIndexRequest var1);

    void open(OpenIndexRequest var1, ActionListenerOpenIndexResponse var2);

    OpenIndexRequestBuilder prepareOpen(String... var1);

    ActionFutureRefreshResponse refresh(RefreshRequest var1);

    void refresh(RefreshRequest var1, ActionListenerRefreshResponse var2);

    RefreshRequestBuilder prepareRefresh(String... var1);

    ActionFutureFlushResponse flush(FlushRequest var1);

    void flush(FlushRequest var1, ActionListenerFlushResponse var2);

    FlushRequestBuilder prepareFlush(String... var1);

    ActionFutureSyncedFlushResponse syncedFlush(SyncedFlushRequest var1);

    void syncedFlush(SyncedFlushRequest var1, ActionListenerSyncedFlushResponse var2);

    SyncedFlushRequestBuilder prepareSyncedFlush(String... var1);

    ActionFutureForceMergeResponse forceMerge(ForceMergeRequest var1);

    void forceMerge(ForceMergeRequest var1, ActionListenerForceMergeResponse var2);

    ForceMergeRequestBuilder prepareForceMerge(String... var1);

    ActionFutureUpgradeResponse upgrade(UpgradeRequest var1);

    void upgrade(UpgradeRequest var1, ActionListenerUpgradeResponse var2);

    UpgradeStatusRequestBuilder prepareUpgradeStatus(String... var1);

    ActionFutureUpgradeStatusResponse upgradeStatus(UpgradeStatusRequest var1);

    void upgradeStatus(UpgradeStatusRequest var1, ActionListenerUpgradeStatusResponse var2);

    UpgradeRequestBuilder prepareUpgrade(String... var1);

    void getMappings(GetMappingsRequest var1, ActionListenerGetMappingsResponse var2);

    ActionFutureGetMappingsResponse getMappings(GetMappingsRequest var1);

    GetMappingsRequestBuilder prepareGetMappings(String... var1);

    void getFieldMappings(GetFieldMappingsRequest var1, ActionListenerGetFieldMappingsResponse var2);

    GetFieldMappingsRequestBuilder prepareGetFieldMappings(String... var1);

    ActionFutureGetFieldMappingsResponse getFieldMappings(GetFieldMappingsRequest var1);

    ActionFuturePutMappingResponse putMapping(PutMappingRequest var1);

    void putMapping(PutMappingRequest var1, ActionListenerPutMappingResponse var2);

    PutMappingRequestBuilder preparePutMapping(String... var1);

    ActionFutureIndicesAliasesResponse aliases(IndicesAliasesRequest var1);

    void aliases(IndicesAliasesRequest var1, ActionListenerIndicesAliasesResponse var2);

    IndicesAliasesRequestBuilder prepareAliases();

    ActionFutureGetAliasesResponse getAliases(GetAliasesRequest var1);

    void getAliases(GetAliasesRequest var1, ActionListenerGetAliasesResponse var2);

    GetAliasesRequestBuilder prepareGetAliases(String... var1);

    AliasesExistRequestBuilder prepareAliasesExist(String... var1);

    ActionFutureAliasesExistResponse aliasesExist(GetAliasesRequest var1);

    void aliasesExist(GetAliasesRequest var1, ActionListenerAliasesExistResponse var2);

    ActionFutureGetIndexResponse getIndex(GetIndexRequest var1);

    void getIndex(GetIndexRequest var1, ActionListenerGetIndexResponse var2);

    GetIndexRequestBuilder prepareGetIndex();

    ActionFutureClearIndicesCacheResponse clearCache(ClearIndicesCacheRequest var1);

    void clearCache(ClearIndicesCacheRequest var1, ActionListenerClearIndicesCacheResponse var2);

    ClearIndicesCacheRequestBuilder prepareClearCache(String... var1);

    ActionFutureUpdateSettingsResponse updateSettings(UpdateSettingsRequest var1);

    void updateSettings(UpdateSettingsRequest var1, ActionListenerUpdateSettingsResponse var2);

    UpdateSettingsRequestBuilder prepareUpdateSettings(String... var1);

    ActionFutureAnalyzeResponse analyze(AnalyzeRequest var1);

    void analyze(AnalyzeRequest var1, ActionListenerAnalyzeResponse var2);

    AnalyzeRequestBuilder prepareAnalyze(@Nullable String var1, String var2);

    AnalyzeRequestBuilder prepareAnalyze(String var1);

    AnalyzeRequestBuilder prepareAnalyze();

    ActionFuturePutIndexTemplateResponse putTemplate(PutIndexTemplateRequest var1);

    void putTemplate(PutIndexTemplateRequest var1, ActionListenerPutIndexTemplateResponse var2);

    PutIndexTemplateRequestBuilder preparePutTemplate(String var1);

    ActionFutureDeleteIndexTemplateResponse deleteTemplate(DeleteIndexTemplateRequest var1);

    void deleteTemplate(DeleteIndexTemplateRequest var1, ActionListenerDeleteIndexTemplateResponse var2);

    DeleteIndexTemplateRequestBuilder prepareDeleteTemplate(String var1);

    ActionFutureGetIndexTemplatesResponse getTemplates(GetIndexTemplatesRequest var1);

    void getTemplates(GetIndexTemplatesRequest var1, ActionListenerGetIndexTemplatesResponse var2);

    GetIndexTemplatesRequestBuilder prepareGetTemplates(String... var1);

    ActionFutureValidateQueryResponse validateQuery(ValidateQueryRequest var1);

    void validateQuery(ValidateQueryRequest var1, ActionListenerValidateQueryResponse var2);

    ValidateQueryRequestBuilder prepareValidateQuery(String... var1);

    void getSettings(GetSettingsRequest var1, ActionListenerGetSettingsResponse var2);

    ActionFutureGetSettingsResponse getSettings(GetSettingsRequest var1);

    GetSettingsRequestBuilder prepareGetSettings(String... var1);

    ResizeRequestBuilder prepareResizeIndex(String var1, String var2);

    ActionFutureResizeResponse resizeIndex(ResizeRequest var1);

    void resizeIndex(ResizeRequest var1, ActionListenerResizeResponse var2);

    RolloverRequestBuilder prepareRolloverIndex(String var1);

    ActionFutureRolloverResponse rolloversIndex(RolloverRequest var1);

    void rolloverIndex(RolloverRequest var1, ActionListenerRolloverResponse var2);
}
