import { createHash } from 'node:crypto';
import { existsSync, readFileSync, statSync } from 'node:fs';
import { basename, resolve } from 'node:path';
import { parseDocument } from 'yaml';
import { CliError } from './errors.js';
import { loadCrudSpec } from './crud-spec.js';
import type {
  CrudFieldSpec,
  CrudGenerationLock,
  CrudGenerationPlan,
  CrudSpec,
  GeneratedFile,
  IntegrationFileChange,
  IntegrationOperation,
  RenderedCrudGeneration,
} from './types.js';

export const CRUD_GENERATOR_VERSION = '0.1.0';
export const CRUD_TEMPLATE_VERSION = '1.0.0';

const SUPPORTED_MODULES = {
  procurement: {
    artifactId: 'omni-procurement',
    javaPackage: 'com.omni.procurement',
    entityPrefix: 'Proc',
    tenantEntity: 'ProcTenantEntity',
    pageQuery: 'ProcPageQuery',
    auditSupport: 'ProcAuditSupport',
  },
} as const;

interface ModuleConvention {
  artifactId: string;
  javaPackage: string;
  entityPrefix: string;
  tenantEntity: string;
  pageQuery: string;
  auditSupport: string;
}

interface CrudContext {
  spec: CrudSpec;
  convention: ModuleConvention;
  aggregateKey: string;
  lowerAggregate: string;
  entityName: string;
  packagePath: string;
  permissionActions: string[];
}

/** 只读渲染 CRUD 垂直切片，并完成所有权、语法和登记后置校验。 */
export function renderCrudGeneration(workspaceRoot: string, specFile: string): RenderedCrudGeneration {
  const root = resolve(workspaceRoot);
  const spec = loadCrudSpec(root, specFile);
  const convention = resolveConvention(root, spec);
  const context = createContext(spec, convention);
  const lockTarget = lockPath(context);
  if (existsSync(resolve(root, ...lockTarget.split('/')))) {
    validateExistingGeneration(root, spec, context);
    return {
      plan: createPlan(specFile, context, [], true),
      changes: [],
    };
  }

  const generated = renderGeneratedFiles(context);
  const changes: IntegrationFileChange[] = generated.map((file) => createChange(root, file));
  const registrations: string[] = [];
  addRegistration(changes, registrations, root, moduleChangelog(context),
    (content) => appendYamlInclude(content, databaseChangeSetPath(context)));
  addRegistration(changes, registrations, root, authChangelog(),
    (content) => appendYamlInclude(content, permissionChangeSetPath(context)));
  addRegistration(changes, registrations, root, 'database/seed/manifest.yaml',
    (content) => registerSeedManifest(content, context, requiredGenerated(generated, permissionSeedPath(context)).sha256));
  addRegistration(changes, registrations, root, 'scaffold/catalog/modules.yaml',
    (content) => registerProvisioningAssertion(content, permissionAssertionId(context)));
  addRegistration(changes, registrations, root, 'omni-frontend/src/constants/menu.ts',
    (content) => insertRecordProperty(content, 'menuI18nMap',
      `  '${spec.permissionResource}': '${context.lowerAggregate}.title',`));
  addRegistration(changes, registrations, root, 'omni-frontend/src/router/index.ts',
    (content) => insertRecordProperty(content, 'iconMap', `  '${spec.permissionResource}': 'Collection',`));
  addRegistration(changes, registrations, root, 'omni-frontend/src/locales/zh-CN.ts',
    (content) => insertLocale(content, context, 'zh-CN'));
  addRegistration(changes, registrations, root, 'omni-frontend/src/locales/en-US.ts',
    (content) => insertLocale(content, context, 'en-US'));
  if (spec.dataScope) {
    addRegistration(changes, registrations, root,
      `omni-backend/${convention.artifactId}/src/main/java/${context.packagePath}/security/ProcDataPermissionHandler.java`,
      (content) => registerProcurementDataScope(content, context));
  }

  const lock = createLock(spec, generated, registrations);
  const lockContent = normalize(`${JSON.stringify(lock, null, 2)}\n`);
  changes.push(createChange(root, { path: lockTarget, content: lockContent, sha256: sha256(lockContent) }));
  ensureUniqueTargets(changes);
  validateRendered(root, context, changes);
  return {
    plan: createPlan(specFile, context, changes, false),
    changes,
  };
}

/** 校验工作区中已生成 CRUD 的锁文件与登记项。 */
export function validateExistingCrud(workspaceRoot: string, specFile: string): CrudGenerationLock {
  const root = resolve(workspaceRoot);
  const spec = loadCrudSpec(root, specFile);
  const context = createContext(spec, resolveConvention(root, spec));
  return validateExistingGeneration(root, spec, context);
}

function resolveConvention(workspaceRoot: string, spec: CrudSpec): ModuleConvention {
  const convention = SUPPORTED_MODULES[spec.moduleId as keyof typeof SUPPORTED_MODULES];
  if (convention === undefined) {
    throw new CliError(`CRUD 生成器尚未验证模块 ${spec.moduleId} 的实体基类和审计约定；当前仅支持 procurement`);
  }
  const moduleRoot = resolve(workspaceRoot, 'omni-backend', convention.artifactId);
  if (!existsSync(moduleRoot) || !statSync(moduleRoot).isDirectory()) {
    throw new CliError(`目标后端模块不存在：${convention.artifactId}`);
  }
  return convention;
}

function createContext(spec: CrudSpec, convention: ModuleConvention): CrudContext {
  const aggregateKey = toKebab(spec.aggregateName);
  return {
    spec,
    convention,
    aggregateKey,
    lowerAggregate: lowerFirst(spec.aggregateName),
    entityName: `${convention.entityPrefix}${spec.aggregateName}`,
    packagePath: convention.javaPackage.replaceAll('.', '/'),
    permissionActions: ['list', 'create', 'update', 'delete'],
  };
}

function renderGeneratedFiles(context: CrudContext): GeneratedFile[] {
  const backend = `omni-backend/${context.convention.artifactId}/src`;
  const java = `${backend}/main/java/${context.packagePath}`;
  const test = `${backend}/test/java/${context.packagePath}`;
  const files: Array<[string, string]> = [
    [`${java}/entity/${context.entityName}.java`, renderEntity(context)],
    [`${java}/dto/${context.spec.aggregateName}Query.java`, renderQuery(context)],
    [`${java}/dto/Create${context.spec.aggregateName}Request.java`, renderRequest(context, false)],
    [`${java}/dto/Update${context.spec.aggregateName}Request.java`, renderRequest(context, true)],
    [`${java}/dto/${context.spec.aggregateName}VO.java`, renderVo(context)],
    [`${java}/mapper/${context.entityName}Mapper.java`, renderMapper(context)],
    [`${java}/service/${context.spec.aggregateName}Service.java`, renderService(context)],
    [`${java}/service/impl/${context.spec.aggregateName}ServiceImpl.java`, renderServiceImpl(context)
      .replace('com.omni.common.core.exception.BusinessException', 'com.omni.common.core.result.BusinessException')],
    [`${java}/controller/${context.spec.aggregateName}Controller.java`, renderController(context)],
    [`${test}/generated/${context.spec.aggregateName}GeneratedContractTest.java`, renderBackendContractTest(context)],
    [`omni-frontend/src/api/${context.spec.moduleId}-${context.aggregateKey}.ts`, renderFrontendApi(context)],
    [`omni-frontend/src/views/${context.spec.permissionResource.replaceAll(':', '/')}/index.vue`, formatFrontendView(renderFrontendView(context))],
    [databaseChangeSetPath(context), renderDatabaseChangeSet(context)],
    [permissionSeedPath(context), renderPermissionSeed(context)],
    [permissionChangeSetPath(context), renderPermissionChangeSet(context)],
    [`docs/generated/${context.spec.moduleId}-${context.aggregateKey}.md`, renderDocumentation(context)],
    [`docs/generated/i18n/${context.spec.moduleId}-${context.aggregateKey}.ja-JP.yaml`, renderFutureLocale(context, 'ja-JP')],
    [`docs/generated/i18n/${context.spec.moduleId}-${context.aggregateKey}.ko-KR.yaml`, renderFutureLocale(context, 'ko-KR')],
  ];
  return files.map(([path, content]) => {
    const normalized = normalize(content);
    return { path, content: normalized, sha256: sha256(normalized) };
  });
}

function renderEntity(context: CrudContext): string {
  const imports = new Set<string>([
    'com.baomidou.mybatisplus.annotation.TableName',
    `${context.convention.javaPackage}.entity.${context.convention.tenantEntity}`,
    'lombok.Data',
    'lombok.EqualsAndHashCode',
    'java.io.Serial',
  ]);
  if (context.spec.optimisticLock) imports.add('com.baomidou.mybatisplus.annotation.Version');
  if (context.spec.logicalDelete) imports.add('com.baomidou.mybatisplus.annotation.TableLogic');
  addJavaTypeImports(imports, context.spec.fields);
  const fields = context.spec.fields.map((field) => [
    `    /** ${field.comment}。 */`,
    `    private ${field.javaType} ${field.name};`,
  ].join('\n'));
  if (context.spec.owner === 'user' || context.spec.owner === 'user-and-unit') {
    fields.push('    /** 数据负责人用户 ID。 */\n    private Long ownerUserId;');
  }
  if (context.spec.owner === 'unit' || context.spec.owner === 'user-and-unit') {
    fields.push('    /** 数据负责人组织 ID。 */\n    private Long ownerUnitId;');
  }
  if (context.spec.optimisticLock) fields.push('    /** 乐观锁版本。 */\n    @Version\n    private Integer version;');
  if (context.spec.logicalDelete) fields.push('    /** 逻辑删除标记。 */\n    @TableLogic\n    private Integer deleted;');
  return `package ${context.convention.javaPackage}.entity;\n\n${javaImports(imports)}\n\n/**\n * ${context.spec.displayName}实体。\n *\n * @generated @omni-stack/cli ${CRUD_GENERATOR_VERSION}，禁止直接修改；修改声明后重新生成。\n */\n@Data\n@EqualsAndHashCode(callSuper = true)\n@TableName("${context.spec.tableName}")\npublic class ${context.entityName} extends ${context.convention.tenantEntity} {\n\n    @Serial\n    private static final long serialVersionUID = 1L;\n\n${fields.join('\n\n')}\n}\n`;
}

function renderQuery(context: CrudContext): string {
  const imports = new Set<string>([
    `${context.convention.javaPackage}.dto.${context.convention.pageQuery}`,
    'lombok.Data',
    'lombok.EqualsAndHashCode',
    'java.io.Serial',
  ]);
  addValidationImports(imports, context.spec.fields.filter((field) => field.query), true);
  addJavaTypeImports(imports, context.spec.fields.filter((field) => field.query));
  const fields = context.spec.fields.filter((field) => field.query).flatMap((field) => {
    if (field.queryOperator === 'between') {
      return [queryField(field, `${field.name}Start`), queryField(field, `${field.name}End`)];
    }
    return [queryField(field, field.name)];
  });
  return `package ${context.convention.javaPackage}.dto;\n\n${javaImports(imports)}\n\n/** ${context.spec.displayName}分页查询。 */\n@Data\n@EqualsAndHashCode(callSuper = true)\npublic class ${context.spec.aggregateName}Query extends ${context.convention.pageQuery} {\n\n    @Serial\n    private static final long serialVersionUID = 1L;\n\n${fields.join('\n\n')}\n}\n`;
}

function renderRequest(context: CrudContext, update: boolean): string {
  const formFields = context.spec.fields.filter((field) => field.form && (!update || !field.immutable));
  const imports = new Set<string>(['lombok.Data', 'java.io.Serial', 'java.io.Serializable']);
  addJavaTypeImports(imports, formFields);
  addValidationImports(imports, formFields, false);
  if (update && context.spec.optimisticLock) imports.add('jakarta.validation.constraints.Min');
  if (update && context.spec.optimisticLock) imports.add('jakarta.validation.constraints.NotNull');
  const fields: string[] = [];
  if (update && context.spec.optimisticLock) {
    fields.push('    /** 乐观锁版本。 */\n    @NotNull\n    @Min(0)\n    private Integer version;');
  }
  fields.push(...formFields.map((field) => requestField(field)));
  const name = `${update ? 'Update' : 'Create'}${context.spec.aggregateName}Request`;
  return `package ${context.convention.javaPackage}.dto;\n\n${javaImports(imports)}\n\n/** ${update ? '更新' : '创建'}${context.spec.displayName}请求。 */\n@Data\npublic class ${name} implements Serializable {\n\n    @Serial\n    private static final long serialVersionUID = 1L;\n\n${fields.join('\n\n')}\n}\n`;
}

function renderVo(context: CrudContext): string {
  const imports = new Set<string>(['lombok.Data', 'java.io.Serial', 'java.io.Serializable', 'java.time.LocalDateTime']);
  addJavaTypeImports(imports, context.spec.fields);
  imports.add('com.fasterxml.jackson.annotation.JsonFormat');
  imports.add('tools.jackson.databind.annotation.JsonSerialize');
  imports.add('tools.jackson.databind.ser.std.ToStringSerializer');
  const fields = context.spec.fields.filter((field) => field.detail || field.list).map((field) => {
    const annotations: string[] = [];
    if (field.javaType === 'Long' || field.javaType === 'BigDecimal') annotations.push('    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)\n    @JsonSerialize(using = ToStringSerializer.class)');
    if (field.javaType === 'LocalDateTime') annotations.push('    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")');
    return `    /** ${field.comment}。 */\n${annotations.length ? `${annotations.join('\n')}\n` : ''}    private ${field.javaType} ${field.name};`;
  });
  return `package ${context.convention.javaPackage}.dto;\n\n${javaImports(imports)}\n\n/** ${context.spec.displayName}响应视图。 */\n@Data\npublic class ${context.spec.aggregateName}VO implements Serializable {\n\n    @Serial\n    private static final long serialVersionUID = 1L;\n\n    /** 主键 ID。 */\n    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)\n    @JsonSerialize(using = ToStringSerializer.class)\n    private Long id;\n\n${fields.join('\n\n')}\n\n${context.spec.optimisticLock ? '    /** 乐观锁版本。 */\n    private Integer version;\n\n' : ''}    /** 创建时间。 */\n    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")\n    private LocalDateTime createTime;\n\n    /** 更新时间。 */\n    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")\n    private LocalDateTime updateTime;\n}\n`;
}

function renderMapper(context: CrudContext): string {
  return `package ${context.convention.javaPackage}.mapper;\n\nimport com.baomidou.mybatisplus.core.mapper.BaseMapper;\nimport ${context.convention.javaPackage}.entity.${context.entityName};\n\n/** ${context.spec.displayName} Mapper。 */\npublic interface ${context.entityName}Mapper extends BaseMapper<${context.entityName}> {\n}\n`;
}

function renderService(context: CrudContext): string {
  const name = context.spec.aggregateName;
  return `package ${context.convention.javaPackage}.service;\n\nimport com.omni.common.core.result.PageResult;\nimport ${context.convention.javaPackage}.dto.Create${name}Request;\nimport ${context.convention.javaPackage}.dto.${name}Query;\nimport ${context.convention.javaPackage}.dto.${name}VO;\nimport ${context.convention.javaPackage}.dto.Update${name}Request;\n\n/** ${context.spec.displayName}服务。 */\npublic interface ${name}Service {\n\n    PageResult<${name}VO> page(${name}Query query);\n\n    ${name}VO get(Long id);\n\n    ${name}VO create(Create${name}Request request);\n\n    ${name}VO update(Long id, Update${name}Request request);\n\n    void delete(Long id${context.spec.optimisticLock ? ', Integer version' : ''});\n}\n`;
}

function renderServiceImpl(context: CrudContext): string {
  const spec = context.spec;
  const name = spec.aggregateName;
  const entity = context.entityName;
  const queryConditions = spec.fields.filter((field) => field.query).map((field) => renderQueryCondition(entity, field)).join('\n');
  const createAssignments = spec.fields.filter((field) => field.form).map((field) => `        entity.set${upperFirst(field.name)}(request.get${upperFirst(field.name)}());`).join('\n');
  const updateAssignments = spec.fields.filter((field) => field.form && !field.immutable)
    .map((field) => `                .set(${entity}::get${upperFirst(field.name)}, request.get${upperFirst(field.name)}())`).join('\n');
  const voAssignments = spec.fields.filter((field) => field.detail || field.list)
    .map((field) => `        result.set${upperFirst(field.name)}(entity.get${upperFirst(field.name)}());`).join('\n');
  const versionFilter = spec.optimisticLock ? `\n                .eq(${entity}::getVersion, request.getVersion())` : '';
  const versionSet = spec.optimisticLock ? '\n                .setSql("version = version + 1")' : '';
  const deletedFilter = spec.logicalDelete ? `\n                .eq(${entity}::getDeleted, 0)` : '';
  const deletedSet = spec.logicalDelete ? `\n                .set(${entity}::getDeleted, 1)` : '';
  const sortMethod = spec.defaultSort.direction === 'asc' ? 'orderByAsc' : 'orderByDesc';
  const duplicateImport = spec.uniqueConstraints.length > 0 ? 'import org.springframework.dao.DuplicateKeyException;\n' : '';
  const createTryOpen = spec.uniqueConstraints.length > 0 ? '        try {\n            mapper.insert(entity);\n        } catch (DuplicateKeyException exception) {\n            throw new BusinessException(409, "唯一字段组合已存在");\n        }' : '        mapper.insert(entity);';
  return `package ${context.convention.javaPackage}.service.impl;\n\nimport com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;\nimport com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;\nimport com.baomidou.mybatisplus.extension.plugins.pagination.Page;\nimport com.omni.common.core.exception.BusinessException;\nimport com.omni.common.core.result.PageResult;\nimport com.omni.common.service.identity.ServiceIdentityContext;\nimport ${context.convention.javaPackage}.dto.Create${name}Request;\nimport ${context.convention.javaPackage}.dto.${name}Query;\nimport ${context.convention.javaPackage}.dto.${name}VO;\nimport ${context.convention.javaPackage}.dto.Update${name}Request;\nimport ${context.convention.javaPackage}.entity.${entity};\nimport ${context.convention.javaPackage}.mapper.${entity}Mapper;\nimport ${context.convention.javaPackage}.service.${name}Service;\nimport ${context.convention.javaPackage}.service.support.${context.convention.auditSupport};\nimport lombok.RequiredArgsConstructor;\n${duplicateImport}import org.springframework.stereotype.Service;\nimport org.springframework.transaction.annotation.Transactional;\n\n/** ${spec.displayName}服务实现。 */\n@Service\n@RequiredArgsConstructor\npublic class ${name}ServiceImpl implements ${name}Service {\n\n    private final ${entity}Mapper mapper;\n\n    @Override\n    @Transactional(readOnly = true)\n    public PageResult<${name}VO> page(${name}Query query) {\n        Long tenantId = ServiceIdentityContext.requireTenantId();\n        LambdaQueryWrapper<${entity}> wrapper = new LambdaQueryWrapper<${entity}>()\n                .eq(${entity}::getTenantId, tenantId);\n${queryConditions}\n        wrapper.${sortMethod}(${entity}::get${upperFirst(spec.defaultSort.field)})\n                .${sortMethod}(${entity}::getId);\n        Page<${entity}> page = mapper.selectPage(new Page<>(query.getPage(), query.getSize()), wrapper);\n        return new PageResult<>(page.getRecords().stream().map(this::toView).toList(),\n                page.getTotal(), page.getSize(), page.getCurrent());\n    }\n\n    @Override\n    @Transactional(readOnly = true)\n    public ${name}VO get(Long id) {\n        return toView(requireEntity(ServiceIdentityContext.requireTenantId(), id));\n    }\n\n    @Override\n    @Transactional\n    public ${name}VO create(Create${name}Request request) {\n        Long tenantId = ServiceIdentityContext.requireTenantId();\n        ${entity} entity = new ${entity}();\n        entity.setTenantId(tenantId);\n${createAssignments}\n${spec.optimisticLock ? '        entity.setVersion(0);\n' : ''}${spec.logicalDelete ? '        entity.setDeleted(0);\n' : ''}        ${context.convention.auditSupport}.created(entity);\n${createTryOpen}\n        return toView(entity);\n    }\n\n    @Override\n    @Transactional\n    public ${name}VO update(Long id, Update${name}Request request) {\n        Long tenantId = ServiceIdentityContext.requireTenantId();\n        requireEntity(tenantId, id);\n        LambdaUpdateWrapper<${entity}> update = new LambdaUpdateWrapper<${entity}>()\n                .eq(${entity}::getTenantId, tenantId)\n                .eq(${entity}::getId, id)${versionFilter}${deletedFilter}\n${updateAssignments}${versionSet}\n                .set(${entity}::getUpdateBy, ServiceIdentityContext.require().username())\n                .setSql("update_time = CURRENT_TIMESTAMP");\n        requireUpdated(mapper.update(null, update));\n        return get(id);\n    }\n\n    @Override\n    @Transactional\n    public void delete(Long id, ${spec.optimisticLock ? 'Integer version' : ''}) {\n        Long tenantId = ServiceIdentityContext.requireTenantId();\n        requireEntity(tenantId, id);\n        LambdaUpdateWrapper<${entity}> update = new LambdaUpdateWrapper<${entity}>()\n                .eq(${entity}::getTenantId, tenantId)\n                .eq(${entity}::getId, id)${spec.optimisticLock ? `\n                .eq(${entity}::getVersion, version)` : ''}${deletedFilter}${deletedSet}${versionSet}\n                .set(${entity}::getUpdateBy, ServiceIdentityContext.require().username())\n                .setSql("update_time = CURRENT_TIMESTAMP");\n        requireUpdated(mapper.update(null, update));\n    }\n\n    private ${entity} requireEntity(Long tenantId, Long id) {\n        ${entity} entity = mapper.selectOne(new LambdaQueryWrapper<${entity}>()\n                .eq(${entity}::getTenantId, tenantId)\n                .eq(${entity}::getId, id));\n        if (entity == null) {\n            throw new BusinessException(404, "${spec.displayName}不存在");\n        }\n        return entity;\n    }\n\n    private void requireUpdated(int rows) {\n        if (rows != 1) {\n            throw new BusinessException(409, "${spec.displayName}已被其他请求修改");\n        }\n    }\n\n    private ${name}VO toView(${entity} entity) {\n        ${name}VO result = new ${name}VO();\n        result.setId(entity.getId());\n${voAssignments}\n${spec.optimisticLock ? '        result.setVersion(entity.getVersion());\n' : ''}        result.setCreateTime(entity.getCreateTime());\n        result.setUpdateTime(entity.getUpdateTime());\n        return result;\n    }\n}\n`;
}

function renderController(context: CrudContext): string {
  const name = context.spec.aggregateName;
  const permission = context.spec.permissionResource;
  const dataScope = context.spec.dataScope ? '\n    @ServiceDataScope(permissionCode = "PERMISSION")' : '';
  const scopeImport = context.spec.dataScope ? 'import com.omni.common.service.datascope.ServiceDataScope;\n' : '';
  return `package ${context.convention.javaPackage}.controller;\n\nimport com.omni.common.core.operlog.OperLog;\nimport com.omni.common.core.operlog.OperType;\nimport com.omni.common.core.result.PageResult;\nimport com.omni.common.core.result.R;\n${scopeImport}import ${context.convention.javaPackage}.dto.Create${name}Request;\nimport ${context.convention.javaPackage}.dto.${name}Query;\nimport ${context.convention.javaPackage}.dto.${name}VO;\nimport ${context.convention.javaPackage}.dto.Update${name}Request;\nimport ${context.convention.javaPackage}.entity.${context.entityName};\nimport ${context.convention.javaPackage}.service.${name}Service;\nimport jakarta.validation.Valid;\nimport jakarta.validation.constraints.Min;\nimport lombok.RequiredArgsConstructor;\nimport org.springframework.security.access.prepost.PreAuthorize;\nimport org.springframework.validation.annotation.Validated;\nimport org.springframework.web.bind.annotation.DeleteMapping;\nimport org.springframework.web.bind.annotation.GetMapping;\nimport org.springframework.web.bind.annotation.PathVariable;\nimport org.springframework.web.bind.annotation.PostMapping;\nimport org.springframework.web.bind.annotation.PutMapping;\nimport org.springframework.web.bind.annotation.RequestBody;\nimport org.springframework.web.bind.annotation.RequestMapping;\nimport org.springframework.web.bind.annotation.RequestParam;\nimport org.springframework.web.bind.annotation.RestController;\n\n/** ${context.spec.displayName}控制器。 */\n@Validated\n@RestController\n@RequestMapping("${context.spec.apiPath}")\n@RequiredArgsConstructor\npublic class ${name}Controller {\n\n    private final ${name}Service service;\n\n    @GetMapping("/list")\n    @PreAuthorize("hasAuthority('${permission}:list')")${dataScope.replace('PERMISSION', `${permission}:list`)}\n    public R<PageResult<${name}VO>> list(@Valid ${name}Query query) {\n        return R.ok(service.page(query));\n    }\n\n    @GetMapping("/{id}")\n    @PreAuthorize("hasAuthority('${permission}:list')")${dataScope.replace('PERMISSION', `${permission}:list`)}\n    public R<${name}VO> get(@PathVariable Long id) {\n        return R.ok(service.get(id));\n    }\n\n    @PostMapping\n    @PreAuthorize("hasAuthority('${permission}:create')")${dataScope.replace('PERMISSION', `${permission}:create`)}\n    @OperLog(module = "${context.spec.displayName}", operType = OperType.CREATE,\n            entityClass = ${context.entityName}.class, idExpr = "#result.data.id")\n    public R<${name}VO> create(@Valid @RequestBody Create${name}Request request) {\n        return R.ok(service.create(request));\n    }\n\n    @PutMapping("/{id}")\n    @PreAuthorize("hasAuthority('${permission}:update')")${dataScope.replace('PERMISSION', `${permission}:update`)}\n    @OperLog(module = "${context.spec.displayName}", operType = OperType.UPDATE,\n            entityClass = ${context.entityName}.class, idExpr = "#id")\n    public R<${name}VO> update(@PathVariable Long id, @Valid @RequestBody Update${name}Request request) {\n        return R.ok(service.update(id, request));\n    }\n\n    @DeleteMapping("/{id}")\n    @PreAuthorize("hasAuthority('${permission}:delete')")${dataScope.replace('PERMISSION', `${permission}:delete`)}\n    @OperLog(module = "${context.spec.displayName}", operType = OperType.DELETE,\n            entityClass = ${context.entityName}.class, idExpr = "#id")\n    public R<Void> delete(@PathVariable Long id${context.spec.optimisticLock ? ',\n                          @RequestParam @Min(0) Integer version' : ''}) {\n        service.delete(id, ${context.spec.optimisticLock ? 'version' : ''});\n        return R.ok();\n    }\n}\n`;
}

function renderBackendContractTest(context: CrudContext): string {
  return `package ${context.convention.javaPackage}.generated;\n\nimport ${context.convention.javaPackage}.controller.${context.spec.aggregateName}Controller;\nimport ${context.convention.javaPackage}.entity.${context.entityName};\nimport org.junit.jupiter.api.Test;\nimport org.springframework.security.access.prepost.PreAuthorize;\nimport org.springframework.web.bind.annotation.RequestMapping;\n\nimport static org.assertj.core.api.Assertions.assertThat;\n\n/** 生成 CRUD 的权限与路由契约测试。 */\nclass ${context.spec.aggregateName}GeneratedContractTest {\n\n    @Test\n    void shouldKeepRouteAndWritePermissions() throws Exception {\n        RequestMapping mapping = ${context.spec.aggregateName}Controller.class.getAnnotation(RequestMapping.class);\n        assertThat(mapping.value()).containsExactly("${context.spec.apiPath}");\n        assertThat(${context.spec.aggregateName}Controller.class\n                .getMethod("create", com.omni.procurement.dto.Create${context.spec.aggregateName}Request.class)\n                .getAnnotation(PreAuthorize.class).value()).contains("${context.spec.permissionResource}:create");\n        assertThat(${context.entityName}.class.getDeclaredField("serialVersionUID")).isNotNull();\n    }\n}\n`;
}

function renderFrontendApi(context: CrudContext): string {
  const spec = context.spec;
  const visible = spec.fields.filter((field) => field.detail || field.list);
  const query = spec.fields.filter((field) => field.query);
  const create = spec.fields.filter((field) => field.form);
  const update = spec.fields.filter((field) => field.form && !field.immutable);
  return `/** @generated @omni-stack/cli ${CRUD_GENERATOR_VERSION}；请修改声明后重新生成。 */\nimport request from './request'\nimport type { ApiResponse, PageResult } from '@/types/api'\n\nexport interface ${spec.aggregateName} {\n  id: string\n${visible.map(tsField).join('\n')}\n${spec.optimisticLock ? '  version: number\n' : ''}  createTime: string\n  updateTime: string\n}\n\nexport interface ${spec.aggregateName}Query {\n${query.flatMap((field) => field.queryOperator === 'between'
    ? [`  ${field.name}Start?: ${field.typescriptType}`, `  ${field.name}End?: ${field.typescriptType}`]
    : [`  ${field.name}?: ${field.typescriptType}`]).join('\n')}\n  page: number\n  size: number\n}\n\nexport interface Create${spec.aggregateName}Request {\n${create.map(tsField).join('\n')}\n}\n\nexport interface Update${spec.aggregateName}Request {\n${spec.optimisticLock ? '  version: number\n' : ''}${update.map(tsField).join('\n')}\n}\n\nconst basePath = '${spec.apiPath.slice(4)}'\n\nexport function list${spec.aggregateName}(params: ${spec.aggregateName}Query) {\n  return request.get<ApiResponse<PageResult<${spec.aggregateName}>>>(\`${'${basePath}'}/list\`, { params })\n}\n\nexport function get${spec.aggregateName}(id: string) {\n  return request.get<ApiResponse<${spec.aggregateName}>>(\`${'${basePath}'}/${'${id}'}\`)\n}\n\nexport function create${spec.aggregateName}(data: Create${spec.aggregateName}Request) {\n  return request.post<ApiResponse<${spec.aggregateName}>>(basePath, data)\n}\n\nexport function update${spec.aggregateName}(id: string, data: Update${spec.aggregateName}Request) {\n  return request.put<ApiResponse<${spec.aggregateName}>>(\`${'${basePath}'}/${'${id}'}\`, data)\n}\n\nexport function delete${spec.aggregateName}(id: string, version${spec.optimisticLock ? ': number' : '?: number'}) {\n  return request.delete<ApiResponse<void>>(\`${'${basePath}'}/${'${id}'}\`, { params: { version } })\n}\n`;
}

function renderFrontendView(context: CrudContext): string {
  const spec = context.spec;
  const queryFields = spec.fields.filter((field) => field.query);
  const listFields = spec.fields.filter((field) => field.list);
  const formFields = spec.fields.filter((field) => field.form);
  const immutable = new Set(spec.fields.filter((field) => field.immutable).map((field) => field.name));
  const apiName = `${spec.moduleId}-${context.aggregateKey}`;
  const initialForm = formFields.map((field) => `  ${field.name}: ${tsDefault(field)},`).join('\n');
  const queryTemplate = queryFields.map((field) => renderVueQuery(field, context)).join('\n');
  const columns = listFields.map((field) => `        <el-table-column prop="${field.name}" :label="t('${context.lowerAggregate}.fields.${field.name}')" min-width="120" />`).join('\n');
  const controls = formFields.map((field) => renderVueControl(field, context, immutable.has(field.name))).join('\n');
  return `<script setup lang="ts">\n/** @generated @omni-stack/cli ${CRUD_GENERATOR_VERSION}；请修改声明后重新生成。 */\nimport { onMounted, reactive, ref } from 'vue'\nimport { useI18n } from 'vue-i18n'\nimport { ElMessage, ElMessageBox } from 'element-plus'\nimport type { FormInstance } from 'element-plus'\nimport {\n  create${spec.aggregateName},\n  delete${spec.aggregateName},\n  get${spec.aggregateName},\n  list${spec.aggregateName},\n  update${spec.aggregateName},\n  type ${spec.aggregateName},\n  type ${spec.aggregateName}Query,\n  type Create${spec.aggregateName}Request,\n} from '@/api/${apiName}'\n\nconst { t } = useI18n()\nconst loading = ref(false)\nconst records = ref<${spec.aggregateName}[]>([])\nconst total = ref(0)\nconst query = reactive<${spec.aggregateName}Query>({ page: 1, size: 10 })\nconst dialogVisible = ref(false)\nconst detailVisible = ref(false)\nconst formRef = ref<FormInstance>()\nconst editing = ref<${spec.aggregateName}>()\nconst detail = ref<${spec.aggregateName}>()\nconst form = reactive<Create${spec.aggregateName}Request & { version?: number }>({\n${initialForm}\n})\n\nasync function load() {\n  loading.value = true\n  try {\n    const response = await list${spec.aggregateName}(query)\n    records.value = response.data.data.records\n    total.value = response.data.data.total\n  } finally {\n    loading.value = false\n  }\n}\n\nfunction search() {\n  query.page = 1\n  load()\n}\n\nfunction reset() {\n${queryFields.map((field) => `  query.${field.name} = undefined`).join('\n')}\n  search()\n}\n\nfunction openCreate() {\n  editing.value = undefined\n  Object.assign(form, {\n${initialForm}\n    version: undefined,\n  })\n  dialogVisible.value = true\n}\n\nasync function openEdit(row: ${spec.aggregateName}) {\n  const response = await get${spec.aggregateName}(row.id)\n  editing.value = response.data.data\n  Object.assign(form, response.data.data)\n  dialogVisible.value = true\n}\n\nasync function save() {\n  if (!(await formRef.value?.validate().catch(() => false))) return\n  if (editing.value) {\n    await update${spec.aggregateName}(editing.value.id, {\n${spec.optimisticLock ? '      version: form.version ?? editing.value.version,\n' : ''}${formFields.filter((field) => !field.immutable).map((field) => `      ${field.name}: form.${field.name},`).join('\n')}\n    })\n  } else {\n    await create${spec.aggregateName}({\n${formFields.map((field) => `      ${field.name}: form.${field.name},`).join('\n')}\n    })\n  }\n  ElMessage.success(t('${context.lowerAggregate}.messages.saved'))\n  dialogVisible.value = false\n  await load()\n}\n\nasync function showDetail(row: ${spec.aggregateName}) {\n  detail.value = (await get${spec.aggregateName}(row.id)).data.data\n  detailVisible.value = true\n}\n\nasync function remove(row: ${spec.aggregateName}) {\n  await ElMessageBox.confirm(t('${context.lowerAggregate}.messages.deleteConfirm'), t('common.confirm'))\n  await delete${spec.aggregateName}(row.id, row.version)\n  ElMessage.success(t('${context.lowerAggregate}.messages.deleted'))\n  await load()\n}\n\nonMounted(load)\n</script>\n\n<template>\n  <section class="crud-page">\n    <el-card shadow="never">\n      <template #header>\n        <div class="header">\n          <div>\n            <h2>{{ t('${context.lowerAggregate}.title') }}</h2>\n            <p>{{ t('${context.lowerAggregate}.description') }}</p>\n          </div>\n          <el-button v-permission="'${spec.permissionResource}:create'" type="primary" @click="openCreate">\n            {{ t('common.create') }}\n          </el-button>\n        </div>\n      </template>\n      <el-form inline class="filters" @submit.prevent="search">\n${queryTemplate}\n        <el-form-item>\n          <el-button type="primary" @click="search">{{ t('common.search') }}</el-button>\n          <el-button @click="reset">{{ t('common.reset') }}</el-button>\n        </el-form-item>\n      </el-form>\n      <el-table v-loading="loading" :data="records">\n${columns}\n        <el-table-column :label="t('common.actions')" fixed="right" width="220">\n          <template #default="{ row }">\n            <el-button link @click="showDetail(row)">{{ t('common.view') }}</el-button>\n            <el-button v-permission="'${spec.permissionResource}:update'" link type="primary" @click="openEdit(row)">{{ t('common.edit') }}</el-button>\n            <el-button v-permission="'${spec.permissionResource}:delete'" link type="danger" @click="remove(row)">{{ t('common.delete') }}</el-button>\n          </template>\n        </el-table-column>\n      </el-table>\n      <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" layout="total, sizes, prev, pager, next" @change="load" />\n    </el-card>\n\n    <el-dialog v-model="dialogVisible" :title="editing ? t('common.edit') : t('common.create')" width="min(640px, 94vw)">\n      <el-form ref="formRef" :model="form" label-position="top">\n${controls}\n      </el-form>\n      <template #footer>\n        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>\n        <el-button type="primary" @click="save">{{ t('common.save') }}</el-button>\n      </template>\n    </el-dialog>\n\n    <el-drawer v-model="detailVisible" :title="t('${context.lowerAggregate}.detail')" size="min(520px, 92vw)">\n      <el-descriptions v-if="detail" :column="1" border>\n${spec.fields.filter((field) => field.detail).map((field) => `        <el-descriptions-item :label="t('${context.lowerAggregate}.fields.${field.name}')">{{ detail.${field.name} }}</el-descriptions-item>`).join('\n')}\n      </el-descriptions>\n    </el-drawer>\n  </section>\n</template>\n\n<style scoped>\n.crud-page { min-width: 0; }\n.header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }\nh2 { margin: 0; }\np { margin: 8px 0 0; color: var(--el-text-color-secondary); }\n.filters { margin-bottom: 12px; }\n.el-pagination { justify-content: flex-end; margin-top: 16px; }\n@media (max-width: 640px) {\n  .header { align-items: stretch; flex-direction: column; }\n  :deep(.el-form--inline .el-form-item) { display: flex; margin-right: 0; width: 100%; }\n  :deep(.el-form-item__content) { min-width: 0; }\n}\n</style>\n`;
}

function renderDatabaseChangeSet(context: CrudContext): string {
  const spec = context.spec;
  const guardColumns = spec.logicalDelete ? spec.uniqueConstraints.map((key) => {
    const sourceName = key.fields.findLast((field) => !['tenantId', 'ownerUserId', 'ownerUnitId'].includes(field));
    if (!sourceName) throw new CliError(`逻辑删除唯一约束 ${key.name} 缺少业务字段`);
    const source = spec.fields.find((field) => field.name === sourceName);
    if (!source) throw new CliError(`逻辑删除唯一约束 ${key.name} 的守卫字段必须是声明字段`);
    return { key, source, column: `${source.columnName}_active_guard` };
  }) : [];
  const columns = [
    '                `id` bigint NOT NULL AUTO_INCREMENT COMMENT \'主键 ID\'',
    ...(spec.tenant ? ['                `tenant_id` bigint NOT NULL COMMENT \'租户 ID\''] : []),
    ...spec.fields.map((field) => `                \`${field.columnName}\` ${databaseColumn(field)} ${field.required ? 'NOT NULL' : 'DEFAULT NULL'} COMMENT '${sqlText(field.comment)}'`),
    ...(spec.owner === 'user' || spec.owner === 'user-and-unit' ? ['                `owner_user_id` bigint NOT NULL COMMENT \'负责人用户 ID\''] : []),
    ...(spec.owner === 'unit' || spec.owner === 'user-and-unit' ? ['                `owner_unit_id` bigint NOT NULL COMMENT \'负责人组织 ID\''] : []),
    ...(spec.optimisticLock ? ['                `version` int NOT NULL DEFAULT 0 COMMENT \'乐观锁版本\''] : []),
    ...(spec.logicalDelete ? ['                `deleted` tinyint NOT NULL DEFAULT 0 COMMENT \'逻辑删除标记\''] : []),
    ...guardColumns.map((guard) => `                \`${guard.column}\` ${databaseColumn(guard.source)} GENERATED ALWAYS AS (CASE WHEN \`deleted\` = 0 THEN \`${guard.source.columnName}\` ELSE NULL END) STORED COMMENT '有效记录唯一键守卫'`),
    '                `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT \'创建时间\'',
    '                `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT \'更新时间\'',
    '                `create_by` varchar(64) DEFAULT NULL COMMENT \'创建人\'',
    '                `update_by` varchar(64) DEFAULT NULL COMMENT \'更新人\'',
    '                PRIMARY KEY (`id`)',
    ...spec.uniqueConstraints.map((key) => {
      const guard = guardColumns.find((item) => item.key.name === key.name);
      return `                UNIQUE KEY \`${key.name}\` (${key.fields.map((field) => `\`${guard?.source.name === field ? guard.column : columnFor(spec, field)}\``).join(',')})`;
    }),
    ...spec.indexes.map((key) => `                KEY \`${key.name}\` (${key.fields.map((field) => `\`${columnFor(spec, field)}\``).join(',')})`),
  ];
  return `databaseChangeLog:\n  - changeSet:\n      id: ${spec.moduleId}-generated-${context.aggregateKey}-0001-create\n      author: omni-cli\n      labels: database:${spec.moduleId},adoption-upgrade\n      comment: 创建${spec.displayName}标准主数据表\n      preConditions:\n        - onFail: HALT\n        - dbms:\n            type: mysql\n        - not:\n            - tableExists:\n                tableName: ${spec.tableName}\n      changes:\n        - sql:\n            splitStatements: true\n            stripComments: false\n            sql: |\n              CREATE TABLE \`${spec.tableName}\` (\n${columns.join(',\n')}\n              ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='${sqlText(spec.displayName)}';\n      rollback:\n        - sql:\n            sql: SELECT 1;\n`;
}

/** 将动态字段片段对齐到 Vue/TypeScript 的项目级缩进规则。 */
function formatFrontendView(content: string): string {
  let insideResetObject = false;
  return content.split('\n').map((line) => {
    if (line === '  Object.assign(form, {') {
      insideResetObject = true;
      return line;
    }
    if (insideResetObject && line === '  })') {
      insideResetObject = false;
      return line;
    }
    if (insideResetObject && /^  [a-z][A-Za-z0-9]*:/.test(line)) return `  ${line}`;
    return line;
  }).join('\n');
}

function renderPermissionSeed(context: CrudContext): string {
  const spec = context.spec;
  const codes = [spec.permissionResource, ...context.permissionActions.map((action) => `${spec.permissionResource}:${action}`)];
  const apiNames: Record<string, string> = { list: '查看', create: '创建', update: '更新', delete: '删除' };
  const menu = permissionInsert(spec.permissionResource, spec.displayName, 'MENU', spec.menuParent, 90);
  const apis = context.permissionActions.map((action, index) => permissionInsert(
    `${spec.permissionResource}:${action}`, `${apiNames[action]}${spec.displayName}`, 'API', spec.permissionResource, index + 1,
  )).join('\n');
  return `-- generated-by: @omni-stack/cli ${CRUD_GENERATOR_VERSION}；模板 ${CRUD_TEMPLATE_VERSION}\n-- ${spec.displayName}权限与默认角色授权（自然键幂等）\n${menu}\n${apis}\nINSERT INTO sys_role_permission (role_id, permission_id)\nSELECT role.id, permission.id\nFROM sys_role role\nJOIN sys_permission permission ON permission.tenant_id = role.tenant_id\nWHERE role.tenant_id = (SELECT id FROM sys_tenant WHERE tenant_code = 'default')\n  AND role.role_code IN (${spec.roleCodes.map((role) => `'${role}'`).join(', ')})\n  AND permission.permission_code IN (${codes.map((code) => `'${code}'`).join(', ')})\n  AND NOT EXISTS (\n      SELECT 1 FROM sys_role_permission existing\n      WHERE existing.role_id = role.id AND existing.permission_id = permission.id\n  );\n`;
}

function renderPermissionChangeSet(context: CrudContext): string {
  return `databaseChangeLog:\n  - changeSet:\n      id: auth-generated-${context.spec.moduleId}-${context.aggregateKey}-permissions\n      author: omni-cli\n      labels: database:auth,adoption-upgrade\n      comment: 按自然键登记${context.spec.displayName}菜单、接口权限和角色授权\n      preConditions:\n        - onFail: HALT\n        - dbms:\n            type: mysql\n        - tableExists:\n            tableName: sys_permission\n      changes:\n        - sqlFile:\n            path: ${permissionSeedPath(context)}\n            encoding: UTF-8\n            splitStatements: true\n            stripComments: true\n      rollback:\n        - sql:\n            sql: SELECT 1;\n`;
}

function renderDocumentation(context: CrudContext): string {
  const spec = context.spec;
  return `# ${spec.displayName}（生成模块）\n\n> 生成器：@omni-stack/cli ${CRUD_GENERATOR_VERSION}；模板：${CRUD_TEMPLATE_VERSION}。本文件及锁文件登记的 generated 文件禁止直接修改。\n\n## 功能\n\n提供分页查询、详情、创建、更新和逻辑删除，页面入口权限为 \`${spec.permissionResource}\`。该聚合是单表主数据，不包含审批、Saga、库存或跨服务事务。\n\n## 数据表\n\n表：\`${spec.tableName}\`。\n\n| 字段 | 数据库列 | 类型 | 必填 | 列表 | 查询 | 表单 |\n|---|---|---|---|---|---|---|\n${spec.fields.map((field) => `| ${field.comment} | \`${field.columnName}\` | ${field.databaseType}${field.length ? `(${field.length})` : ''} | ${field.required ? '是' : '否'} | ${field.list ? '是' : '否'} | ${field.queryOperator} | ${field.form ? '是' : '否'} |`).join('\n')}\n\n## API 与权限\n\n| 操作 | 方法与路径 | 权限 |\n|---|---|---|\n| 列表 | GET ${spec.apiPath}/list | ${spec.permissionResource}:list |\n| 详情 | GET ${spec.apiPath}/{id} | ${spec.permissionResource}:list |\n| 创建 | POST ${spec.apiPath} | ${spec.permissionResource}:create |\n| 更新 | PUT ${spec.apiPath}/{id} | ${spec.permissionResource}:update |\n| 删除 | DELETE ${spec.apiPath}/{id}?version=... | ${spec.permissionResource}:delete |\n\n## 第一次使用\n\n1. 使用包含 ${spec.roleCodes.join('、')} 角色的账号登录。\n2. 在“${spec.displayName}”页面使用筛选、分页和详情。\n3. 创建后编辑记录时会携带 version；并发修改冲突返回 409。\n4. 删除为逻辑删除，不会物理 DROP 数据。\n\n## 维护与再生成\n\n- 声明：\`scaffold/specs/${context.aggregateKey}.yaml\`。\n- 锁文件：\`${lockPath(context)}\`。\n- generated 文件发生人工修改时 CLI 会停止并报告漂移。\n- 已应用 changeSet 永不改写；结构演进必须新增 forward-only changeSet。\n- 复杂状态机、审批、PII 推断和跨服务一致性必须手写并评审。\n`;
}

function renderFutureLocale(context: CrudContext, locale: 'ja-JP' | 'ko-KR'): string {
  const translations = locale === 'ja-JP'
    ? { title: '資材ブランド', description: '資材ブランドのマスターデータを管理します' }
    : { title: '자재 브랜드', description: '자재 브랜드 기준 정보를 관리합니다' };
  return `# ${locale} 将在全局语言包 WP-09B 启用；当前作为生成契约保留。\n${context.lowerAggregate}:\n  title: "${translations.title}"\n  description: "${translations.description}"\n${context.spec.fields.map((field) => `  fields.${field.name}: "${field.comment}"`).join('\n')}\n`;
}

function createChange(workspaceRoot: string, file: GeneratedFile): IntegrationFileChange {
  if (existsSync(resolve(workspaceRoot, ...file.path.split('/')))) {
    throw new CliError(`生成目标已存在且不归当前锁文件所有：${file.path}`);
  }
  return { target: file.path, mode: 'create', after: file.content };
}

function addRegistration(
  changes: IntegrationFileChange[],
  registrations: string[],
  workspaceRoot: string,
  target: string,
  render: (content: string) => string,
): void {
  const absolute = resolve(workspaceRoot, ...target.split('/'));
  const before = readFileSync(absolute, 'utf8');
  const after = normalize(render(before));
  if (before.replace(/\r\n/g, '\n') === after) throw new CliError(`登记项已存在但缺少 CRUD 锁文件：${target}`);
  changes.push({ target, mode: 'modify', before, after });
  registrations.push(target);
}

function validateExistingGeneration(workspaceRoot: string, spec: CrudSpec, context: CrudContext): CrudGenerationLock {
  const target = lockPath(context);
  let lock: CrudGenerationLock;
  try {
    lock = JSON.parse(readFileSync(resolve(workspaceRoot, ...target.split('/')), 'utf8')) as CrudGenerationLock;
  } catch {
    throw new CliError(`CRUD 锁文件不是合法 JSON：${target}`);
  }
  if (!isCrudLock(lock) || lock.templateVersion !== CRUD_TEMPLATE_VERSION) {
    throw new CliError(`CRUD 锁文件所有权或模板版本不匹配：${target}`);
  }
  if (lock.specSha256 !== specSha(spec)) {
    throw new CliError('CRUD 声明已变化；为保护已执行 changeSet，当前版本拒绝覆盖，请先生成结构演进计划');
  }
  for (const file of lock.files) {
    const absolute = resolve(workspaceRoot, ...file.path.split('/'));
    if (!existsSync(absolute) || !statSync(absolute).isFile()) throw new CliError(`生成文件缺失：${file.path}`);
    const actual = sha256(readFileSync(absolute, 'utf8').replace(/\r\n/g, '\n'));
    if (actual !== file.sha256) {
      throw new CliError(`生成文件发生人工漂移：${file.path}；拒绝覆盖，请还原或人工三方合并`);
    }
  }
  validateRegistrations(workspaceRoot, context, lock.registrations);
  return lock;
}

function validateRegistrations(workspaceRoot: string, context: CrudContext, registrations: string[]): void {
  const required: Array<[string, string]> = [
    [moduleChangelog(context), databaseChangeSetPath(context)],
    [authChangelog(), permissionChangeSetPath(context)],
    ['database/seed/manifest.yaml', permissionSourceId(context)],
    ['database/seed/manifest.yaml', permissionAssertionId(context)],
    ['scaffold/catalog/modules.yaml', permissionAssertionId(context)],
    ['omni-frontend/src/constants/menu.ts', context.spec.permissionResource],
    ['omni-frontend/src/router/index.ts', context.spec.permissionResource],
  ];
  if (context.spec.dataScope) {
    required.push([
      `omni-backend/${context.convention.artifactId}/src/main/java/${context.packagePath}/security/ProcDataPermissionHandler.java`,
      `"${context.spec.tableName}"`,
    ]);
  }
  for (const [target, value] of required) {
    if (!registrations.includes(target) || !readFileSync(resolve(workspaceRoot, ...target.split('/')), 'utf8').includes(value)) {
      throw new CliError(`CRUD 登记项缺失：${target} -> ${value}`);
    }
  }
}

function createLock(spec: CrudSpec, files: GeneratedFile[], registrations: string[]): CrudGenerationLock {
  return {
    generatedBy: '@omni-stack/cli',
    generatorVersion: CRUD_GENERATOR_VERSION,
    templateVersion: CRUD_TEMPLATE_VERSION,
    specSha256: specSha(spec),
    spec,
    files: files.map((file) => ({ path: file.path, sha256: file.sha256 })),
    registrations,
  };
}

function createPlan(
  specFile: string,
  context: CrudContext,
  changes: IntegrationFileChange[],
  unchanged: boolean,
): CrudGenerationPlan {
  const operations: IntegrationOperation[] = changes.map((change) => ({
    kind: change.mode === 'create' ? 'create-file' : operationKind(change.target),
    target: change.target,
    description: change.mode === 'create' ? '创建受锁文件管理的生成文件' : '登记生成区块',
  }));
  return {
    aggregateKey: `${context.spec.moduleId}-${context.aggregateKey}`,
    specFile,
    operations,
    conflicts: [],
    warnings: context.spec.dataScope ? ['DataScope 生成仍需模块策略专项验收'] : [],
    ready: true,
    unchanged,
  };
}

function validateRendered(workspaceRoot: string, context: CrudContext, changes: IntegrationFileChange[]): void {
  const byTarget = new Map(changes.map((change) => [change.target, change.after]));
  for (const target of [databaseChangeSetPath(context), permissionChangeSetPath(context), moduleChangelog(context), authChangelog(), 'database/seed/manifest.yaml', 'scaffold/catalog/modules.yaml']) {
    const content = byTarget.get(target);
    if (content === undefined) throw new CliError(`渲染结果缺失：${target}`);
    const document = parseDocument(content);
    if (document.errors.length > 0) throw new CliError(`渲染 YAML 无效：${target}: ${document.errors[0]?.message}`);
  }
  const service = byTarget.get(`omni-backend/${context.convention.artifactId}/src/main/java/${context.packagePath}/service/${context.spec.aggregateName}Service.java`);
  if (service?.includes('delete(Long id, );')) throw new CliError('未启用乐观锁时生成了非法删除签名');
  const view = byTarget.get(`omni-frontend/src/views/${context.spec.permissionResource.replaceAll(':', '/')}/index.vue`);
  if (!view?.includes(`v-permission="'${context.spec.permissionResource}:create'"`)) {
    throw new CliError('生成页面缺少创建权限控制');
  }
  const lock = byTarget.get(lockPath(context));
  if (lock === undefined) throw new CliError('渲染结果缺少 CRUD 锁文件');
  JSON.parse(lock);
  validateSeedManifestReferences(workspaceRoot, byTarget.get('database/seed/manifest.yaml')!, context, byTarget.get(permissionSeedPath(context))!);
}

function validateSeedManifestReferences(workspaceRoot: string, content: string, context: CrudContext, seed: string): void {
  const root = parseDocument(content).toJS() as { sources?: Array<{ id?: string; sha256?: string }>; assertions?: Array<{ id?: string }> };
  const source = root.sources?.find((item) => item.id === permissionSourceId(context));
  if (source?.sha256 !== sha256(seed)) throw new CliError('seed manifest 中的生成权限摘要不匹配');
  if (!root.assertions?.some((item) => item.id === permissionAssertionId(context))) {
    throw new CliError('seed manifest 缺少生成权限断言');
  }
  if (!existsSync(resolve(workspaceRoot, 'database/seed/manifest.yaml'))) throw new CliError('工作区缺少 seed manifest');
}

function appendYamlInclude(content: string, file: string): string {
  const root = parseDocument(content).toJS() as { databaseChangeLog?: Array<{ include?: { file?: string } }> };
  if (root.databaseChangeLog?.some((entry) => entry.include?.file === file)) throw new CliError(`changelog 已登记：${file}`);
  return `${content.replace(/\s*$/, '')}\n  - include:\n      file: ${file}\n`;
}

function registerSeedManifest(content: string, context: CrudContext, seedSha: string): string {
  const root = parseDocument(content).toJS() as { sources?: Array<{ id?: string }>; assertions?: Array<{ id?: string }> };
  if (root.sources?.some((item) => item.id === permissionSourceId(context))) throw new CliError('seed source 已存在');
  if (root.assertions?.some((item) => item.id === permissionAssertionId(context))) throw new CliError('seed assertion 已存在');
  const sourceAnchor = '# 兼容期模块 ID 镜像；';
  if (content.split(sourceAnchor).length !== 2) throw new CliError('seed manifest source 锚点不唯一');
  const source = `  - id: ${permissionSourceId(context)}\n    module: auth\n    resource: ${permissionSeedPath(context)}\n    sha256: "${seedSha}"\n\n`;
  const withSource = content.replace(sourceAnchor, `${source}${sourceAnchor}`);
  const codes = [context.spec.permissionResource, ...context.permissionActions.map((action) => `${context.spec.permissionResource}:${action}`)];
  const assertion = `\n\n  - id: ${permissionAssertionId(context)}\n    module: auth\n    database: omni_auth\n    query: >-\n      SELECT CONCAT_WS('|', permission.permission_code, permission.type,\n      COALESCE(parent.permission_code, '')) AS seed_key\n      FROM sys_permission permission LEFT JOIN sys_permission parent ON parent.id = permission.parent_id\n      WHERE permission.tenant_id = (SELECT id FROM sys_tenant WHERE tenant_code = 'default')\n      AND permission.permission_code IN (${codes.map((code) => `'${code}'`).join(', ')})\n    expectedRows: ${codes.length}\n    expectedSha256: "${permissionAssertionSha(context)}"\n`;
  return `${withSource.replace(/\s*$/, '')}${assertion}`;
}

function registerProvisioningAssertion(content: string, assertionId: string): string {
  if (content.includes(`      - ${assertionId}`)) throw new CliError(`catalog 已登记：${assertionId}`);
  const start = content.indexOf('  - id: auth\n');
  const end = content.indexOf('\n  - id: ', start + 1);
  if (start < 0 || end < 0) throw new CliError('catalog 缺少唯一 auth 模块区块');
  const block = content.slice(start, end);
  const anchor = '      - auth-xss-defaults';
  if (block.split(anchor).length !== 2) throw new CliError('catalog auth provisioning 锚点不唯一');
  const updated = block.replace(anchor, `${anchor}\n      - ${assertionId}`);
  return `${content.slice(0, start)}${updated}${content.slice(end)}`;
}

function registerProcurementDataScope(content: string, context: CrudContext): string {
  const entry = `            "${context.spec.tableName}", new ScopeColumns("owner_user_id", "owner_unit_id"),`;
  if (content.includes(`"${context.spec.tableName}"`)) throw new CliError(`DataScope handler 已登记表：${context.spec.tableName}`);
  const anchor = '    private static final Map<String, ScopeColumns> ROOT_TABLES = Map.of(\n';
  if (content.split(anchor).length !== 2) throw new CliError('ProcDataPermissionHandler ROOT_TABLES 锚点不唯一');
  return content.replace(anchor, `${anchor}${entry}\n`);
}

function insertRecordProperty(content: string, variable: string, line: string): string {
  if (content.includes(line.trim())) throw new CliError(`${variable} 已包含生成属性`);
  const declaration = `const ${variable}`;
  const start = content.indexOf(declaration);
  const end = content.indexOf('\n}', start);
  if (start < 0 || end < 0) throw new CliError(`找不到 ${variable} 对象`);
  return `${content.slice(0, end)}\n${line}${content.slice(end)}`;
}

function insertLocale(content: string, context: CrudContext, locale: 'zh-CN' | 'en-US'): string {
  const key = context.lowerAggregate;
  if (new RegExp(`\\n  ${key}: \\{`).test(content)) throw new CliError(`语言包已包含 ${key}`);
  const last = content.lastIndexOf('\n}') ;
  if (last < 0) throw new CliError(`语言包缺少根对象：${locale}`);
  const text = locale === 'zh-CN'
    ? { title: context.spec.displayName, description: `维护${context.spec.displayName}主数据`, detail: `${context.spec.displayName}详情`, saved: '保存成功', deleted: '删除成功', confirm: `确认删除该${context.spec.displayName}？` }
    : { title: 'Material Brands', description: 'Maintain material brand master data', detail: 'Material brand details', saved: 'Saved', deleted: 'Deleted', confirm: 'Delete this material brand?' };
  const fields = context.spec.fields.map((field) => `      ${field.name}: '${locale === 'zh-CN' ? sqlText(field.comment) : humanize(field.name)}',`).join('\n');
  const block = `  ${key}: {\n    title: '${sqlText(text.title)}',\n    description: '${sqlText(text.description)}',\n    detail: '${sqlText(text.detail)}',\n    fields: {\n${fields}\n    },\n    messages: {\n      saved: '${sqlText(text.saved)}',\n      deleted: '${sqlText(text.deleted)}',\n      deleteConfirm: '${sqlText(text.confirm)}',\n    },\n  },`;
  return `${content.slice(0, last)}\n${block}${content.slice(last)}`;
}

function permissionInsert(code: string, name: string, type: 'MENU' | 'API', parentCode: string, sort: number): string {
  return `INSERT INTO sys_permission\n    (tenant_id, parent_id, permission_code, permission_name, type, path, depth, sort, status, create_by)\nSELECT tenant.id, parent.id, '${code}', '${sqlText(name)}', '${type}', '', parent.depth + 1, ${sort}, 1, 'system'\nFROM sys_tenant tenant\nJOIN sys_permission parent ON parent.tenant_id = tenant.id AND parent.permission_code = '${parentCode}'\nWHERE tenant.tenant_code = 'default'\n  AND NOT EXISTS (\n      SELECT 1 FROM sys_permission existing\n      WHERE existing.tenant_id = tenant.id AND existing.permission_code = '${code}'\n  );\n\nUPDATE sys_permission permission\nJOIN sys_tenant tenant ON tenant.id = permission.tenant_id AND tenant.tenant_code = 'default'\nJOIN sys_permission parent ON parent.id = permission.parent_id AND parent.tenant_id = permission.tenant_id\nSET permission.path = CONCAT(parent.path, permission.id, '/')\nWHERE permission.permission_code = '${code}';\n`;
}

function renderQueryCondition(entity: string, field: CrudFieldSpec): string {
  const getter = `${entity}::get${upperFirst(field.name)}`;
  if (field.queryOperator === 'between') {
    return `        if (query.get${upperFirst(field.name)}Start() != null) wrapper.ge(${getter}, query.get${upperFirst(field.name)}Start());\n        if (query.get${upperFirst(field.name)}End() != null) wrapper.le(${getter}, query.get${upperFirst(field.name)}End());`;
  }
  const method = field.queryOperator;
  if (field.javaType === 'String') {
    return `        if (query.get${upperFirst(field.name)}() != null && !query.get${upperFirst(field.name)}().isBlank()) {\n            wrapper.${method}(${getter}, query.get${upperFirst(field.name)}().trim());\n        }`;
  }
  return `        if (query.get${upperFirst(field.name)}() != null) wrapper.${method}(${getter}, query.get${upperFirst(field.name)}());`;
}

function requestField(field: CrudFieldSpec): string {
  const annotations: string[] = [];
  if (field.required) annotations.push(field.javaType === 'String' ? '@NotBlank' : '@NotNull');
  if (field.javaType === 'String' && field.length) annotations.push(`@Size(max = ${field.length})`);
  return `    /** ${field.comment}。 */\n${annotations.map((value) => `    ${value}`).join('\n')}${annotations.length ? '\n' : ''}    private ${field.javaType} ${field.name};`;
}

function queryField(field: CrudFieldSpec, name: string): string {
  const annotation = field.javaType === 'String' && field.length ? `    @Size(max = ${field.length})\n` : '';
  return `    /** ${field.comment}查询条件。 */\n${annotation}    private ${field.javaType} ${name};`;
}

function addValidationImports(imports: Set<string>, fields: CrudFieldSpec[], query: boolean): void {
  if (fields.some((field) => !query && field.required && field.javaType === 'String')) imports.add('jakarta.validation.constraints.NotBlank');
  if (fields.some((field) => !query && field.required && field.javaType !== 'String')) imports.add('jakarta.validation.constraints.NotNull');
  if (fields.some((field) => field.javaType === 'String' && field.length)) imports.add('jakarta.validation.constraints.Size');
}

function addJavaTypeImports(imports: Set<string>, fields: CrudFieldSpec[]): void {
  const mapping: Record<string, string> = {
    LocalDate: 'java.time.LocalDate',
    LocalDateTime: 'java.time.LocalDateTime',
    BigDecimal: 'java.math.BigDecimal',
  };
  fields.forEach((field) => {
    const value = mapping[field.javaType];
    if (value) imports.add(value);
  });
}

function javaImports(imports: Set<string>): string {
  return [...imports].sort().map((value) => `import ${value};`).join('\n');
}

function tsField(field: CrudFieldSpec): string {
  return `  ${field.name}${field.required ? '' : '?'}: ${field.typescriptType}${field.required ? '' : ' | null'}`;
}

function tsDefault(field: CrudFieldSpec): string {
  if (!field.required) return 'undefined';
  if (field.typescriptType === 'string') return "''";
  if (field.typescriptType === 'boolean') return 'false';
  return field.name === 'status' ? '1' : '0';
}

function renderVueQuery(field: CrudFieldSpec, context: CrudContext): string {
  return `        <el-form-item :label="t('${context.lowerAggregate}.fields.${field.name}')">\n          <el-input v-model="query.${field.name}" clearable />\n        </el-form-item>`;
}

function renderVueControl(field: CrudFieldSpec, context: CrudContext, immutable: boolean): string {
  const disabled = immutable ? ' :disabled="Boolean(editing)"' : '';
  const model = `form.${field.name}`;
  const control = field.control === 'textarea'
    ? `<el-input v-model="${model}" type="textarea" :rows="3"${disabled} />`
    : field.control === 'select'
      ? `<el-select v-model="${model}"${disabled}><el-option :label="t('common.enabled')" :value="1" /><el-option :label="t('common.disabled')" :value="0" /></el-select>`
      : field.control === 'switch'
        ? `<el-switch v-model="${model}"${disabled} />`
        : `<el-input v-model="${model}"${disabled} />`;
  return `        <el-form-item :label="t('${context.lowerAggregate}.fields.${field.name}')" prop="${field.name}">\n          ${control}\n        </el-form-item>`;
}

function databaseColumn(field: CrudFieldSpec): string {
  return switchValue(field.databaseType, {
    VARCHAR: `varchar(${field.length})`,
    TEXT: 'text',
    INT: 'int',
    BIGINT: 'bigint',
    TINYINT: 'tinyint',
    DATE: 'date',
    DATETIME: 'datetime',
    DECIMAL: `decimal(${field.decimalPrecision},${field.decimalScale})`,
  });
}

function columnFor(spec: CrudSpec, field: string): string {
  const generated: Record<string, string> = {
    id: 'id', tenantId: 'tenant_id', ownerUserId: 'owner_user_id', ownerUnitId: 'owner_unit_id',
    version: 'version', deleted: 'deleted', createTime: 'create_time', updateTime: 'update_time',
  };
  return generated[field] ?? spec.fields.find((item) => item.name === field)?.columnName
    ?? (() => { throw new CliError(`无法解析数据库列：${field}`); })();
}

function operationKind(target: string): IntegrationOperation['kind'] {
  if (target.endsWith('.yaml') || target.endsWith('.yml')) return 'modify-yaml';
  if (target.endsWith('.ts')) return 'modify-typescript';
  return 'modify-java';
}

function moduleChangelog(context: CrudContext): string {
  return `database/changelog/${context.spec.moduleId}/db.changelog-${context.spec.moduleId}.yaml`;
}

function authChangelog(): string {
  return 'database/changelog/auth/db.changelog-auth.yaml';
}

function databaseChangeSetPath(context: CrudContext): string {
  return `database/changelog/${context.spec.moduleId}/generated-${context.aggregateKey}-0001.yaml`;
}

function permissionSeedPath(context: CrudContext): string {
  return `scripts/sql/seed/${context.spec.moduleId}-${context.aggregateKey}-permissions.sql`;
}

function permissionChangeSetPath(context: CrudContext): string {
  return `database/changelog/auth/generated-${context.spec.moduleId}-${context.aggregateKey}-permissions.yaml`;
}

function lockPath(context: CrudContext): string {
  return `scaffold/locks/crud/${context.spec.moduleId}-${context.aggregateKey}.lock.json`;
}

function permissionSourceId(context: CrudContext): string {
  return `auth-${context.spec.moduleId}-${context.aggregateKey}-permissions`;
}

function permissionAssertionId(context: CrudContext): string {
  return `auth-${context.spec.moduleId}-${context.aggregateKey}-permission-catalog`;
}

function permissionAssertionSha(context: CrudContext): string {
  const values = [
    `${context.spec.permissionResource}|MENU|${context.spec.menuParent}`,
    ...context.permissionActions.map((action) => `${context.spec.permissionResource}:${action}|API|${context.spec.permissionResource}`),
  ].map((value) => `seed_key:12=${canonicalSeedValue(value)}`).sort();
  return sha256(values.join('\n'));
}

function canonicalSeedValue(value: string): string {
  return value.replaceAll('\\', '\\\\').replaceAll('|', '\\|').replaceAll('=', '\\=')
    .replaceAll('\r', '\\r').replaceAll('\n', '\\n').replaceAll('\t', '\\t');
}

function requiredGenerated(files: GeneratedFile[], path: string): GeneratedFile {
  const file = files.find((item) => item.path === path);
  if (!file) throw new CliError(`缺少生成文件：${path}`);
  return file;
}

function ensureUniqueTargets(changes: IntegrationFileChange[]): void {
  const targets = new Set<string>();
  for (const change of changes) {
    if (targets.has(change.target)) throw new CliError(`CRUD 变更目标重复：${change.target}`);
    targets.add(change.target);
  }
}

function isCrudLock(value: unknown): value is CrudGenerationLock {
  if (!value || typeof value !== 'object') return false;
  const lock = value as Partial<CrudGenerationLock>;
  return lock.generatedBy === '@omni-stack/cli'
    && typeof lock.generatorVersion === 'string'
    && typeof lock.templateVersion === 'string'
    && typeof lock.specSha256 === 'string'
    && Array.isArray(lock.files)
    && lock.files.every((file) => typeof file?.path === 'string' && /^[a-f0-9]{64}$/.test(file.sha256))
    && Array.isArray(lock.registrations);
}

function specSha(spec: CrudSpec): string {
  return sha256(JSON.stringify(spec));
}

function sha256(value: string): string {
  return createHash('sha256').update(value).digest('hex');
}

function normalize(value: string): string {
  return `${value.replace(/\r\n/g, '\n').replace(/\n*$/, '')}\n`;
}

function toKebab(value: string): string {
  return value.replace(/([a-z0-9])([A-Z])/g, '$1-$2').toLowerCase();
}

function lowerFirst(value: string): string {
  return `${value.charAt(0).toLowerCase()}${value.slice(1)}`;
}

function upperFirst(value: string): string {
  return `${value.charAt(0).toUpperCase()}${value.slice(1)}`;
}

function humanize(value: string): string {
  return value.replace(/([a-z])([A-Z])/g, '$1 $2').replace(/^./, (character) => character.toUpperCase());
}

function sqlText(value: string): string {
  return value.replaceAll('\\', '\\\\').replaceAll("'", "''").replaceAll('\r', ' ').replaceAll('\n', ' ');
}

function switchValue<T extends string>(key: T, values: Record<T, string>): string {
  return values[key];
}
