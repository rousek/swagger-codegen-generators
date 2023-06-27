package io.swagger.codegen.v3.generators.typescript;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.swagger.codegen.v3.*;
import io.swagger.codegen.v3.generators.DefaultCodegenConfig;
import io.swagger.codegen.v3.utils.ModelUtils;
import io.swagger.codegen.v3.utils.SemVer;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.BinarySchema;
import io.swagger.v3.oas.models.media.FileSchema;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.util.SchemaTypeUtil;
import org.apache.commons.lang3.StringUtils;
import org.mozilla.javascript.optimizer.Codegen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.swagger.codegen.v3.CodegenConstants.IS_ENUM_EXT_NAME;
import static io.swagger.codegen.v3.generators.handlebars.ExtensionHelper.getBooleanValue;

public class TypeScriptAngularClientCodegen extends AbstractTypeScriptClientCodegen {

    private static Logger LOGGER = LoggerFactory.getLogger(TypeScriptAngularClientCodegen.class);

    private static final SimpleDateFormat SNAPSHOT_SUFFIX_FORMAT = new SimpleDateFormat("yyyyMMddHHmm");

    public static final String NPM_NAME = "npmName";
    public static final String NPM_VERSION = "npmVersion";
    public static final String NPM_REPOSITORY = "npmRepository";
    public static final String SNAPSHOT = "snapshot";
    public static final String WITH_INTERFACES = "withInterfaces";
    public static final String NG_VERSION = "ngVersion";
    public static final String NG_PACKAGR = "useNgPackagr";
    public static final String PROVIDED_IN_ROOT ="providedInRoot";
    public static final String KEBAB_FILE_NAME ="kebab-file-name";

    protected String npmName = null;
    protected String npmVersion = "1.0.0";
    protected String npmRepository = null;
    protected boolean kebabFileNaming;

    public TypeScriptAngularClientCodegen() {
        super();
        this.outputFolder = "generated-code" + File.separator + "typescript-angular";

        this.cliOptions.add(new CliOption(NPM_NAME, "The name under which you want to publish generated npm package"));
        this.cliOptions.add(new CliOption(NPM_VERSION, "The version of your npm package"));
        this.cliOptions.add(new CliOption(NPM_REPOSITORY, "Use this property to set an url your private npmRepo in the package.json"));
        this.cliOptions.add(new CliOption(SNAPSHOT, "When setting this property to true the version will be suffixed with -SNAPSHOT.yyyyMMddHHmm", SchemaTypeUtil.BOOLEAN_TYPE).defaultValue(Boolean.FALSE.toString()));
        this.cliOptions.add(new CliOption(WITH_INTERFACES, "Setting this property to true will generate interfaces next to the default class implementations.", SchemaTypeUtil.BOOLEAN_TYPE).defaultValue(Boolean.FALSE.toString()));
        this.cliOptions.add(new CliOption(NG_VERSION, "The version of Angular. Default is '4.3'"));
        this.cliOptions.add(new CliOption(PROVIDED_IN_ROOT, "Use this property to provide Injectables in root (it is only valid in angular version greater or equal to 6.0.0).", SchemaTypeUtil.BOOLEAN_TYPE).defaultValue(Boolean.TRUE.toString()));
    }

    @Override
    protected void addAdditionPropertiesToCodeGenModel(CodegenModel codegenModel, Schema schema) {
        if (schema instanceof MapSchema  && hasSchemaProperties(schema)) {
            codegenModel.additionalPropertiesType = getTypeDeclaration((Schema) schema.getAdditionalProperties());
            addImport(codegenModel, codegenModel.additionalPropertiesType);
        } else if (schema instanceof MapSchema && hasTrueAdditionalProperties(schema)) {
            codegenModel.additionalPropertiesType = getTypeDeclaration(new ObjectSchema());
        }
    }

    @Override
    public String getName() {
        return "typescript-angular";
    }

    @Override
    public String getHelp() {
        return "Generates a TypeScript Angular (2.x or 4.x) client library.";
    }

    @Override
    public void processOpts() {
        super.processOpts();

        modelTemplateFiles.put("model.mustache", ".ts");
        apiTemplateFiles.put("api.service.mustache", ".ts");

        languageSpecificPrimitives.add("Blob");
        typeMapping.put("file", "Blob");
        apiPackage = "api";
        modelPackage = "model";

        supportingFiles.add(
                new SupportingFile("models.mustache", modelPackage().replace('.', '/'), "models.ts"));
        supportingFiles
                .add(new SupportingFile("apis.mustache", apiPackage().replace('.', '/'), "api.ts"));
        supportingFiles.add(new SupportingFile("index.mustache", getIndexDirectory(), "index.ts"));
        //supportingFiles.add(new SupportingFile("api.module.mustache", getIndexDirectory(), "api.module.ts"));
        //supportingFiles.add(new SupportingFile("configuration.mustache", getIndexDirectory(), "configuration.ts"));
        //supportingFiles.add(new SupportingFile("variables.mustache", getIndexDirectory(), "variables.ts"));
        //supportingFiles.add(new SupportingFile("encoder.mustache", getIndexDirectory(), "encoder.ts"));
        //supportingFiles.add(new SupportingFile("gitignore", "", ".gitignore"));
        //supportingFiles.add(new SupportingFile("npmignore", "", ".npmignore"));
        //supportingFiles.add(new SupportingFile("git_push.sh.mustache", "", "git_push.sh"));

        SemVer ngVersion = determineNgVersion();

        additionalProperties.put(NG_VERSION, ngVersion);

        // for Angular 2 AOT support we will use good-old ngc,
        // Angular Package format wasn't invented at this time and building was much more easier
        if (!ngVersion.atLeast("4.0.0")) {
            LOGGER.warn("Please update your legacy Angular " + ngVersion + " project to benefit from 'Angular Package Format' support.");
            additionalProperties.put(NG_PACKAGR, false);
        } else {
            additionalProperties.put(NG_PACKAGR, true);
        }

        // Set the rxJS version compatible to the Angular version
        if (ngVersion.atLeast("8.0.0")) {
            additionalProperties.put("rxjsVersion", "6.5.0");
            additionalProperties.put("useRxJS6", true);
        } else if (ngVersion.atLeast("7.0.0")) {
            additionalProperties.put("rxjsVersion", "6.3.0");
            additionalProperties.put("useRxJS6", true);
        } else if (ngVersion.atLeast("6.0.0")) {
            additionalProperties.put("rxjsVersion", "6.1.0");
            additionalProperties.put("useRxJS6", true);
        } else {
            // Angular prior to v6
            additionalProperties.put("rxjsVersion", "5.4.0");
        }
        if (!ngVersion.atLeast("4.3.0")) {
            supportingFiles.add(new SupportingFile("rxjs-operators.mustache", getIndexDirectory(), "rxjs-operators.ts"));
        }

        // Version after Angular 10 require ModuleWithProviders to be generic. Compatible from version 7.
        if (ngVersion.atLeast("7.0.0")) {
            additionalProperties.put("genericModuleWithProviders", true);
        }

        // for Angular 2 AOT support we will use good-old ngc,
        // Angular Package format wasn't invented at this time and building was much more easier
        if (!ngVersion.atLeast("4.0.0")) {
            LOGGER.warn("Please update your legacy Angular " + ngVersion + " project to benefit from 'Angular Package Format' support.");
            additionalProperties.put("useNgPackagr", false);
        } else {
            additionalProperties.put("useNgPackagr", true);
            //supportingFiles.add(new SupportingFile("ng-package.mustache", getIndexDirectory(), "ng-package.json"));
        }

        // Libraries generated with v1.x of ng-packagr will ship with AoT metadata in v3, which is intended for Angular v4.
        // Libraries generated with v2.x of ng-packagr will ship with AoT metadata in v4, which is intended for Angular v5 (and Angular v6).
        additionalProperties.put("useOldNgPackagr", !ngVersion.atLeast("5.0.0"));

        // set http client usage
        if (ngVersion.atLeast("8.0.0")) {
            additionalProperties.put("useHttpClient", true);
        } else if (ngVersion.atLeast("4.3.0")) {
            additionalProperties.put("useHttpClient", true);
        } else {
            additionalProperties.put("useHttpClient", false);
        }

        if (additionalProperties.containsKey(PROVIDED_IN_ROOT) && !ngVersion.atLeast("6.0.0")) {
            additionalProperties.put(PROVIDED_IN_ROOT,false);
        }

        additionalProperties.put("injectionToken", ngVersion.atLeast("4.0.0") ? "InjectionToken" : "OpaqueToken");
        additionalProperties.put("injectionTokenTyped", ngVersion.atLeast("4.0.0"));

        if (additionalProperties.containsKey(NPM_NAME)) {
            addNpmPackageGeneration(ngVersion);
        }

        if (additionalProperties.containsKey(WITH_INTERFACES)) {
            boolean withInterfaces = Boolean.parseBoolean(additionalProperties.get(WITH_INTERFACES).toString());
            if (withInterfaces) {
                apiTemplateFiles.put("apiInterface.mustache", "Interface.ts");
            }
        }

        kebabFileNaming = true;//Boolean.parseBoolean(String.valueOf(additionalProperties.get(KEBAB_FILE_NAME)));

    }

    private SemVer determineNgVersion() {
        SemVer ngVersion;
        if (additionalProperties.containsKey(NG_VERSION)) {
            ngVersion = new SemVer(additionalProperties.get(NG_VERSION).toString());
        } else {
            ngVersion = new SemVer("8.0.0");
            LOGGER.info("generating code for Angular {} ...", ngVersion);
            LOGGER.info("  (you can select the angular version by setting the additionalProperty ngVersion)");
        }
        return ngVersion;
    }

    private void addNpmPackageGeneration(SemVer ngVersion) {
        if (additionalProperties.containsKey(NPM_NAME)) {
            this.setNpmName(additionalProperties.get(NPM_NAME).toString());
        }

        if (additionalProperties.containsKey(NPM_VERSION)) {
            this.setNpmVersion(additionalProperties.get(NPM_VERSION).toString());
        }

        if (additionalProperties.containsKey(SNAPSHOT) && Boolean.parseBoolean(additionalProperties.get(SNAPSHOT).toString())) {
            this.setNpmVersion(npmVersion + "-SNAPSHOT." + SNAPSHOT_SUFFIX_FORMAT.format(new Date()));
        }
        additionalProperties.put(NPM_VERSION, npmVersion);

        if (additionalProperties.containsKey(NPM_REPOSITORY)) {
            this.setNpmRepository(additionalProperties.get(NPM_REPOSITORY).toString());
        }

        additionalProperties.put("useHttpClientPackage", false);
        if (ngVersion.atLeast("15.0.0")) {
            additionalProperties.put("tsVersion", ">=4.8.2 <4.10.0");
            additionalProperties.put("rxjsVersion", "7.5.5");
            additionalProperties.put("ngPackagrVersion", "15.0.2");
            additionalProperties.put("zonejsVersion", "0.11.5");
        } else if (ngVersion.atLeast("14.0.0")) {
            additionalProperties.put("tsVersion", ">=4.6.0 <=4.8.0");
            additionalProperties.put("rxjsVersion", "7.5.5");
            additionalProperties.put("ngPackagrVersion", "14.0.2");
            additionalProperties.put("zonejsVersion", "0.11.5");
        } else if (ngVersion.atLeast("13.0.0")) {
            additionalProperties.put("tsVersion", ">=4.4.2 <4.5.0");
            additionalProperties.put("rxjsVersion", "7.4.0");
            additionalProperties.put("ngPackagrVersion", "13.0.3");
            additionalProperties.put("zonejsVersion", "0.11.4");
        } else if (ngVersion.atLeast("12.0.0")) {
            additionalProperties.put("tsVersion", ">=4.3.0 <4.4.0");
            additionalProperties.put("rxjsVersion", "7.4.0");
            additionalProperties.put("ngPackagrVersion", "12.2.1");
            additionalProperties.put("zonejsVersion", "0.11.4");
        } else if (ngVersion.atLeast("11.0.0")) {
            additionalProperties.put("tsVersion", ">=4.0.0 <4.1.0");
            additionalProperties.put("rxjsVersion", "6.6.0");
            additionalProperties.put("ngPackagrVersion", "11.0.2");
            additionalProperties.put("zonejsVersion", "0.11.3");
        } else if (ngVersion.atLeast("10.0.0")) {
            additionalProperties.put("tsVersion", ">=3.9.2 <4.0.0");
            additionalProperties.put("rxjsVersion", "6.6.0");
            additionalProperties.put("ngPackagrVersion", "10.0.3");
            additionalProperties.put("zonejsVersion", "0.10.2");
        } else if (ngVersion.atLeast("9.0.0")) {
            additionalProperties.put("tsVersion", ">=3.6.0 <3.8.0");
            additionalProperties.put("rxjsVersion", "6.5.3");
            additionalProperties.put("ngPackagrVersion", "9.0.1");
            additionalProperties.put("zonejsVersion", "0.10.2");
        } else if (ngVersion.atLeast("8.0.0")) {
            additionalProperties.put("tsVersion", ">=3.4.0 <3.6.0");
            additionalProperties.put("rxjsVersion", "6.5.0");
            additionalProperties.put("ngPackagrVersion", "5.4.0");
            additionalProperties.put("zonejsVersion", "0.9.1");
        } else if (ngVersion.atLeast("7.0.0")) {
            additionalProperties.put("tsVersion", ">=3.1.1 <3.2.0");
            additionalProperties.put("rxjsVersion", "6.3.0");
            additionalProperties.put("ngPackagrVersion", "5.1.0");
            additionalProperties.put("zonejsVersion", "0.8.26");

            additionalProperties.put("useHttpClientPackage", true);
        } else if (ngVersion.atLeast("6.0.0")) {
            additionalProperties.put("tsVersion", ">=2.7.2 and <2.10.0");
            additionalProperties.put("rxjsVersion", "6.1.0");
            additionalProperties.put("ngPackagrVersion", "3.0.6");
            additionalProperties.put("zonejsVersion", "0.8.26");

            additionalProperties.put("useHttpClientPackage", true);
        } else {
            additionalProperties.put("tsVersion", ">=2.1.5 and <2.8");
            additionalProperties.put("rxjsVersion", "6.1.0");
            additionalProperties.put("ngPackagrVersion", "3.0.6");
            additionalProperties.put("zonejsVersion", "0.8.26");

            additionalProperties.put("useHttpClientPackage", true);
        }

        //Files for building our lib
        supportingFiles.add(new SupportingFile("README.mustache", getIndexDirectory(), "README.md"));
        supportingFiles.add(new SupportingFile("package.mustache", getIndexDirectory(), "package.json"));
        supportingFiles.add(new SupportingFile("typings.mustache", getIndexDirectory(), "typings.json"));
        supportingFiles.add(new SupportingFile("tsconfig.mustache", getIndexDirectory(), "tsconfig.json"));
        if (additionalProperties.containsKey(NG_PACKAGR)
            && Boolean.valueOf(additionalProperties.get(NG_PACKAGR).toString())) {
            supportingFiles.add(new SupportingFile("ng-package.mustache", getIndexDirectory(), "ng-package.json"));
        }
    }

    private String getIndexDirectory() {
        String indexPackage = modelPackage.substring(0, Math.max(0, modelPackage.lastIndexOf('.')));
        return indexPackage.replace('.', File.separatorChar);
    }

    @Override
    public boolean isDataTypeFile(final String dataType) {
        return dataType != null && dataType.equals("Blob");
    }

    @Override
    public String getArgumentsLocation() {
        return null;
    }

    @Override
    public String getDefaultTemplateDir() {
        return "typescript-angular";
    }

    @Override
    public String getTypeDeclaration(Schema propertySchema) {
        Schema inner;
        if(propertySchema instanceof ArraySchema) {
            ArraySchema arraySchema = (ArraySchema)propertySchema;
            inner = arraySchema.getItems();
            return this.getSchemaType(propertySchema) + "<" + this.getTypeDeclaration(inner) + ">";
        } else if(propertySchema instanceof MapSchema   && hasSchemaProperties(propertySchema)) {
            inner = (Schema) propertySchema.getAdditionalProperties();
            return "{ [key: string]: " + this.getTypeDeclaration(inner) + "; }";
        } else if (propertySchema instanceof MapSchema && hasTrueAdditionalProperties(propertySchema)) {
            inner = new ObjectSchema();
            return "{ [key: string]: " + this.getTypeDeclaration(inner) + "; }";
        } else if(propertySchema instanceof FileSchema || propertySchema instanceof BinarySchema) {
            return "Blob";
        } else if(propertySchema instanceof ObjectSchema) {
            return "any";
        } else {
            return super.getTypeDeclaration(propertySchema);
        }
    }

    @Override
    public String getSchemaType(Schema schema) {
        String swaggerType = super.getSchemaType(schema);
        if(isLanguagePrimitive(swaggerType) || isLanguageGenericType(swaggerType)) {
            return swaggerType;
        }
        applyLocalTypeMapping(swaggerType);
        return swaggerType;
    }

    private String applyLocalTypeMapping(String type) {
        if (typeMapping.containsKey(type)) {
            type = typeMapping.get(type);
        }
        return type;
    }

    private boolean isLanguagePrimitive(String type) {
        return languageSpecificPrimitives.contains(type);
    }

    private boolean isLanguageGenericType(String type) {
        for (String genericType : languageGenericTypes) {
            if (type.startsWith(genericType + "<")) {
                return true;
            }
        }
        return false;
    }

    protected void addOperationImports(CodegenOperation codegenOperation, Set<String> operationImports) {
        for (String operationImport : operationImports) {
            if (operationImport.contains("|")) {
                String[] importNames = operationImport.split("\\|");
                for (String importName : importNames) {
                    importName = importName.trim();
                    if (needToImport(importName)) {
                        codegenOperation.imports.add(importName);
                    }
                }
            } else {
                if (needToImport(operationImport)) {
                    codegenOperation.imports.add(operationImport);
                }
            }
        }
    }

    @Override
    public void postProcessParameter(CodegenParameter parameter) {
        super.postProcessParameter(parameter);
        parameter.dataType = applyLocalTypeMapping(parameter.dataType);
    }

    class KnownOperation {
        String name;
        String method;
        List<CodegenParameter> urlParams = new ArrayList<>();
        List<CodegenParameter> queryParams = new ArrayList<>();
        boolean found = false;
        boolean hasBody = false;

        KnownOperation(String name, String method, Boolean hasBody, DefaultCodegenConfig cfg) {
            this.name = cfg.toOperationId(name);
            this.method = method;
            this.hasBody = hasBody;
        }

        boolean compare(CodegenOperation op) {
            return  name.equals(op.getOperationId()) &&
                    method.equals(op.getHttpMethod()) &&
                    hasBody == op.getHasBodyParam() &&
                    compareParams(queryParams, op.queryParams) &&
                    compareParams(urlParams, op.pathParams);
        }

        private List<CodegenParameter> sort(List<CodegenParameter> ops) {
            return ops
                .stream()
                .filter(a -> a != null && a.baseName != null)
                .sorted(Comparator.comparing(a -> a.baseName)).collect(Collectors.toList());
        }

        private boolean compareParams(List<CodegenParameter> l1, List<CodegenParameter> l2) {
            List<CodegenParameter> s1 = sort(l1);
            List<CodegenParameter> s2 = sort(l2);

            if (s1.size() != s2.size()) {
                return false;
            }

            for (int i = 0; i < s1.size(); i++) {
                if (!s1.get(i).baseName.equals(s2.get(i).baseName) ||
                    !s1.get(i).dataType.equals(s2.get(i).dataType) ||
                    s1.get(i).required != s2.get(i).required ||
                    !s1.get(i).dataFormat.equals(s2.get(i).dataFormat)) {
                    return false;
                }
            }
            return true;
        }
    }

    // Replaces CodegenOperation to add isPostOrPut parameter in templates.
    class CodegenOperationWithMethod extends CodegenOperation {
        public boolean getIsPost() { return "post".equals(httpMethod.toLowerCase()); }
        public boolean getIsPut() { return "put".equals(httpMethod.toLowerCase()); }
        public boolean getIsGet() { return "get".equals(httpMethod.toLowerCase()); }
        public boolean getIsDelete() { return "delete".equals(httpMethod.toLowerCase()); }
        public boolean getIsPostOrPut() { return getIsPost() || getIsPut(); }

        public String getPathNoApi() {
            if (path.startsWith("/Api")) {
                return path.substring("/Api".length());
            }
            return path;
        }

        public CodegenOperationWithMethod(CodegenOperation op) {
            this.httpMethod = op.httpMethod;
            this.authMethods = op.authMethods;
            this.path = op.path;
            this.operationId = op.operationId;
            this.baseName = op.baseName;
            this.pathParams = op.pathParams;
            this.nickname = op.nickname;
            this.bodyParams = op.bodyParams;
            this.bodyParam = op.bodyParam;
            this.examples = op.examples;
            this.headerParams = op.headerParams;
            this.formParams = op.formParams;
            this.imports = op.imports;
            this.tags = op.tags;
            this.allParams = op.allParams;
            this.queryParams = op.queryParams;
            this.consumes = op.consumes;
            this.contents = op.contents;
            this.defaultResponse = op.defaultResponse;
            this.discriminator = op.discriminator;
            this.externalDocs = op.externalDocs;
            this.notes = op.notes;
            this.operationIdCamelCase = op.operationIdCamelCase;
            this.operationIdLowerCase = op.operationIdLowerCase;
            this.prioritizedContentTypes = op.prioritizedContentTypes;
            this.produces = op.produces;
            this.responses = op.responses;
            this.returnBaseType = op.returnBaseType;
            this.returnContainer = op.returnContainer;
            this.returnType = op.returnType;
            this.summary = op.summary;
            this.unescapedNotes = op.unescapedNotes;
            this.responseHeaders.clear();
            this.responseHeaders.addAll(op.responseHeaders);
            this.cookieParams = op.cookieParams;
            this.operationIdSnakeCase = op.operationIdSnakeCase;
            this.requestBodyExamples = op.requestBodyExamples;
            this.requiredParams = op.requiredParams;
            this.returnSimpleType = op.returnSimpleType;
            this.returnTypeIsPrimitive = op.returnTypeIsPrimitive;
            this.subresourceOperation = op.subresourceOperation;
            this.testPath = op.testPath;
            this.vendorExtensions = op.vendorExtensions;
        }
    }

    private void printParam(CodegenParameter p) {
        System.out.println("Base name: " + p.baseName);
        System.out.println("Param name: " + p.paramName);
        System.out.println("Datatype: " + p.dataType);
        System.out.println("Required: " + p.required);
        System.out.println("Data format: " + p.dataFormat);
        System.out.println("Enum name: " + p.enumName);
        System.out.println("********************************************");
    }

    private void printOp(CodegenOperation op) {
        System.out.println("OperationId: " + op.operationId);
        System.out.println("Http method: " + op.httpMethod);
        System.out.println("Has body params: " + op.getHasBodyParam());
        System.out.println("Return type: " + op.returnType);
        System.out.println("Return base type: " + op.returnBaseType);
        System.out.println("Return container: " + op.returnContainer);
        System.out.println("Path: " + op.path);
        System.out.println("Path params: ");
        for (CodegenParameter p : op.pathParams) {
            printParam(p);
        }
        System.out.println("********************************************");
        System.out.println("Query params: ");
        for (CodegenParameter p : op.queryParams) {
            printParam(p);
        }
        System.out.println("********************************************");
        System.out.println("********************************************");
    }

    @Override
    public Map<String, Object> postProcessOperations(Map<String, Object> operations) {
        Map<String, Object> objs = (Map<String, Object>) operations.get("operations");

        // Add filename information for api imports
        objs.put("apiFilename", getApiFilenameFromClassname(objs.get("classname").toString()));

        List<CodegenOperation> ops = (List<CodegenOperation>) objs.get("operation");

        CodegenParameter requiredId = new CodegenParameter();
        requiredId.baseName = "id";
        requiredId.dataType = "number";
        requiredId.dataFormat = "int64";
        requiredId.required = true;

        KnownOperation add = new KnownOperation("Add", "GET", false,this);
        KnownOperation item = new KnownOperation("Item", "GET", false, this);
        KnownOperation delete = new KnownOperation("Delete", "DELETE", false, this);
        KnownOperation save = new KnownOperation("Save", "PUT", true, this);
        KnownOperation list = new KnownOperation("List", "GET", false, this);
        KnownOperation history = new KnownOperation("History", "GET", false, this);

        item.urlParams.add(requiredId);
        delete.urlParams.add(requiredId);
        history.urlParams.add(requiredId);

        CodegenParameter datumOd = new CodegenParameter();
        datumOd.baseName = "datumOd";
        datumOd.dataType = "string";
        datumOd.dataFormat = "date-time";
        datumOd.required = false;

        CodegenParameter DatumDo = new CodegenParameter();
        DatumDo.baseName = "DatumDo";
        DatumDo.dataType = "string";
        DatumDo.dataFormat = "date-time";
        DatumDo.required = false;


        CodegenParameter maxPocet = new CodegenParameter();
        maxPocet.baseName = "maxPocet";
        maxPocet.dataType = "number";
        maxPocet.dataFormat = "int32";
        maxPocet.required = false;

        history.queryParams.add(datumOd);
        history.queryParams.add(DatumDo);
        history.queryParams.add(maxPocet);

        KnownOperation[] knownOps = {add, item, delete, save, list, history};

        List<CodegenOperation> toRemove = new ArrayList<>();
        for (int i = 0; i < ops.size(); i++) {
            CodegenOperation op = ops.get(i);
            //printOp(op);

            for (KnownOperation kOp : knownOps) {
                if (kOp.compare(op)) {
                    kOp.found = true;
                    toRemove.add(op);
                }
            }
        }
        boolean extendsGeneric = true;
        for (KnownOperation kOp : knownOps) {
            if (!kOp.found) {
                extendsGeneric = false;
            }
        }

        String dp3Type = null;
        if (extendsGeneric) {
            String entityName = null;
            for (CodegenOperation op : toRemove) {
                if (op.getOperationId().equals(add.name)) {
                    dp3Type = op.returnType;
                    entityName = op.path.substring("/Api".length(), op.path.length() - "/Add".length());
                }
            }
            if (dp3Type != null) {
                ops.removeAll(toRemove);
                objs.put("isDp3Generic", "true");
                objs.put("dp3TypeName", dp3Type);
                objs.put("dp3EntityName", entityName);
            }
        }

        if (!ops.isEmpty()) {
            objs.put("hasExtraMethods", "true");
        }

        for (CodegenOperation op : ops) {
            op.httpMethod = op.httpMethod.toLowerCase(Locale.ENGLISH);

            // Prep a string buffer where we're going to set up our new version of the string.
            StringBuilder pathBuffer = new StringBuilder();
            StringBuilder parameterName = new StringBuilder();
            int insideCurly = 0;

            // Iterate through existing string, one character at a time.
            for (int i = 0; i < op.path.length(); i++) {
                switch (op.path.charAt(i)) {
                case '{':
                    // We entered curly braces, so track that.
                    insideCurly++;

                    // Add the more complicated component instead of just the brace.
                    pathBuffer.append("${encodeURIComponent(String(");
                    break;
                case '}':
                    // We exited curly braces, so track that.
                    insideCurly--;

                    // Add the more complicated component instead of just the brace.
                    pathBuffer.append(toVarName(parameterName.toString()));
                    pathBuffer.append("))}");
                    parameterName.setLength(0);
                    break;
                default:
                    if (insideCurly > 0) {
                        parameterName.append(op.path.charAt(i));
                    } else {
                        pathBuffer.append(op.path.charAt(i));
                    }
                    break;
                }
            }

            // Overwrite path to TypeScript template string, after applying everything we just did.
            op.path = pathBuffer.toString();
        }

        List<CodegenOperationWithMethod> withMethods = new ArrayList<>();
        for (CodegenOperation op : ops) {
            withMethods.add(new CodegenOperationWithMethod(op));
        }
        ops.clear();
        ops.addAll(withMethods);

        // Add additional filename information for model imports in the services
        List<Map<String, Object>> imports = (List<Map<String, Object>>) operations.get("imports");

        for (Map<String, Object> im : imports) {
            im.put("filename", im.get("import"));
            im.put("classname", getModelnameFromModelFilename(im.get("filename").toString()));
        }

        // Remove Datahistory and GridApiResponse from imports if this extends
        // BaseHttpService and none of the other calls return these types.
        if (extendsGeneric) {
            List<Map<String, Object>> importsToRemove = new ArrayList<>();
            boolean hasDataHist = "Datahistory".equals(dp3Type);
            boolean hasGridResp = "GridApiResponse".equals(dp3Type);

            for (CodegenOperation op : ops) {
                if (!hasDataHist && "Datahistory".equals(op.returnBaseType)) {
                    hasDataHist = true;
                } else if (!hasGridResp && "GridApiResponse".equals(op.returnBaseType)) {
                    hasGridResp = true;
                }
                if (hasGridResp && hasDataHist) {
                    break;
                }
            }
            for (Map<String, Object> im : imports) {
                String modelName = im.get("classname").toString();
                if ((!hasDataHist && "Datahistory".equals(modelName)) ||
                    (!hasGridResp && "GridApiResponse".equals(modelName))) {
                    importsToRemove.add(im);
                }
            }
            imports.removeAll(importsToRemove);
        }

        return operations;
    }

    @Override
    public Map<String, Object> postProcessModels(Map<String, Object> objs) {
        Map<String, Object> result = super.postProcessModels(objs);

        // Add additional filename information for imports
        List<Object> models = (List<Object>) postProcessModelsEnum(result).get("models");
        for (Object _mo : models) {
            Map<String, Object> mo = (Map<String, Object>) _mo;
            CodegenModel cm = (CodegenModel) mo.get("model");
            //System.out.println("classname: " + cm.classname + ", filename: " + cm.classFilename + ", name: " + cm.name + ", model name: " + toModelName(cm.name));
            mo.put("tsImports", toTsImports(cm, cm.imports));
        }

        return result;
    }

    @Override
    public Map<String, Object> postProcessAllModels(Map<String, Object> processedModels) {
        for (Map.Entry<String, Object> entry : processedModels.entrySet()) {
            final Map<String, Object> inner = (Map<String, Object>) entry.getValue();
            final List<Map<String, Object>> models = (List<Map<String, Object>>) inner.get("models");
            for (Map<String, Object> mo : models) {
                final CodegenModel codegenModel = (CodegenModel) mo.get("model");
                if (codegenModel.getIsAlias() && codegenModel.imports != null && !codegenModel.imports.isEmpty()) {
                    mo.put("tsImports", toTsImports(codegenModel, codegenModel.imports));
                }
            }
        }
        return processedModels;
    }

    private List<Map<String, String>> toTsImports(CodegenModel cm, Set<String> imports) {
        List<Map<String, String>> tsImports = new ArrayList<>();
        for (String im : imports) {
            if (!im.equals(cm.classname)) {
                HashMap<String, String> tsImport = new HashMap<>();
                tsImport.put("classname", im);
                tsImport.put("filename", toModelFilename(im));
                tsImports.add(tsImport);
            }
        }
        return tsImports;
    }

    @Override
    public String toApiName(String name) {
        if (name.length() == 0) {
            return "DefaultService";
        }
        return name + "Service";
    }

    @Override
    public String toApiFilename(String name) {
        if (name.length() == 0) {
            return "default.service";
        }
        if (kebabFileNaming) {
            return dashize(name) + ".service";
        }
        // Keep original name if all upper case to avoid weird naming.
        if (name.equals(name.toUpperCase())) {
            return name + ".service";
        }
        return camelize(name, true) + ".service";
    }

    @Override
    public String toApiImport(String name) {
        return apiPackage() + "/" + toApiFilename(name);
    }

    @Override
    public String toModelFilename(String name) {
        if (kebabFileNaming) {
            return dashize(name);
        }
        String modelName = toModelName(name);
        if (modelName.equals(modelName.toUpperCase())) {
            return modelName;
        }
        return camelize(modelName, true);
    }

    @Override
    public String toModelName(String name) {
        return super.toModelName(dashize(name));
    }

    @Override
    public String toModelImport(String name) {
        return modelPackage() + "/" + toModelFilename(name);
    }

    public String getNpmName() {
        return npmName;
    }

    public void setNpmName(String npmName) {
        this.npmName = npmName;
    }

    public String getNpmVersion() {
        return npmVersion;
    }

    public void setNpmVersion(String npmVersion) {
        this.npmVersion = npmVersion;
    }

    public String getNpmRepository() {
        return npmRepository;
    }

    public void setNpmRepository(String npmRepository) {
        this.npmRepository = npmRepository;
    }

    private String getApiFilenameFromClassname(String classname) {
        String name = classname.substring(0, classname.length() - "Service".length());
        return toApiFilename(name);
    }

    private String getModelnameFromModelFilename(String filename) {
        String name = filename.substring((modelPackage() + File.separator).length());
        return camelize(name);
    }

    @Override
    protected String getOrGenerateOperationId(Operation operation, String path, String httpMethod) {
        String operationId = operation.getOperationId();
        if (StringUtils.isBlank(operationId)) {
            String tmpPath = path;
            // Removes parameters from path such as /{id}
            tmpPath = tmpPath.replaceAll("\\/\\{[^{]*}", "");
            String[] parts = tmpPath.split("/");
            StringBuilder builder = new StringBuilder();
            // Operation ID is the last non-parameter segment of path
            builder.append(parts[parts.length - 1]);
            operationId = sanitizeName(builder.toString());
            LOGGER.warn("Empty operationId found for path: " + httpMethod + " " + path + ". Renamed to auto-generated operationId: " + operationId);
        }
        return operationId;
    }

    @Override
    public String sanitizeTag(String tag) {
        tag = camelize(sanitizeName(dashize(tag)));

        // tag starts with numbers
        if (tag.matches("^\\d.*")) {
            tag = "Class" + tag;
        }

        return tag;
    }
}
