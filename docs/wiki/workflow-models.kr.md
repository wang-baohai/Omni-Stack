# 4. 프로젝트 구현: 초안을 실행 가능한 버전으로 만들기

편집기의 그림은 아직 논의 중인 승인 규정과 같습니다. 저장은 기록을 남기고, 게시는 새 신청에 그 규정을 쓰도록 허용합니다. 프로젝트는 초안·게시 버전·엔진 정의를 나눕니다.

## 그림 편집과 게시를 구분한다

modelKey는 현재 테넌트 안에서 유일해야 하고 영문자로 시작하며 이후 영문자·숫자·하이픈을 쓸 수 있습니다. 초기 초안의 업무 버전은 0입니다. 저장 시 설계 JSON과 BPMN XML을 받습니다. 백엔드는 JSON만으로 실행 가능한 XML을 만들지 않습니다.

게시 후 수정하면 기존 게시 내용을 유지하는 별도 초안을 만듭니다. 실행 중인 신청은 원래 정의를 사용합니다. 프로젝트의 업무 버전은 해당 모델의 기존 최댓값에 1을 더하며, Flowable 정의 버전은 엔진이 따로 관리합니다. 문서의 동시 수정 버전은 또 다른 값입니다.

## 첫 번째 소스: 잠근 뒤 실제 초안을 확인한다

WorkflowModelServiceImpl 게시 메서드의 시작 부분입니다.

<!-- omni:source path="omni-backend/omni-workflow/src/main/java/com/omni/workflow/service/impl/WorkflowModelServiceImpl.java" -->
```java
    public PublishResult publishModel(Long modelId, String userName) {
        // 1. SELECT FOR UPDATE 锁定模型行
        WfProcessModel model = lockModelForUpdate(modelId);

        // 2. 获取草稿版本
        WfProcessModelVersion draft = versionMapper.selectById(model.getCurrentDraftVersionId());
        if (draft == null || draft.getBpmnXml() == null || draft.getBpmnXml().isBlank()) {
            throw new BusinessException("BPMN XML 为空，无法发布");
        }
        if (!"DRAFT".equals(draft.getStatus())) {
            throw new BusinessException("当前草稿指针指向非 DRAFT 版本(" + draft.getStatus()
                    + ")，请先通过 saveDraft 创建独立草稿");
        }
```

행 잠금은 같은 모델을 게시하는 요청을 줄 세워 두 사람이 같은 초안을 각각 게시하지 못하게 합니다. XML이 없거나 DRAFT가 아니면 거부하고, 다음에 XML을 검증합니다. 로컬 트랜잭션 안의 모델 한 행 잠금이며 다른 업무 서비스까지 묶는 전역 잠금이 아닙니다.

행 잠금은 직접 JDBC로 얻습니다. 잠금 획득과 테넌트 권한 확인은 서로 다른 일입니다.

## XML을 열 수 있다는 것만으로 충분하지 않다

BpmnXmlValidator는 DOCTYPE·외부 엔티티 등을 막고, 실행 가능한 process 한 개, modelKey와 같은 process id, 시작·종료, 승인 할당 확장, 관련 참조 수신 확장, 게이트웨이 기본 연결과 일부 연결 참조를 검사합니다.

assignment JSON의 업무 의미까지 옳다고 증명하지는 않습니다. 스크립트·리스너·표현식에 대한 완전한 안전 허용 목록도 없습니다. 확장이 존재해도 실제 승인자를 찾는다는 보장은 없으므로 역할·조직·업무 시나리오로 확인해야 합니다.

## 두 번째 소스: 테넌트를 명시해 그림을 엔진에 전달한다

<!-- omni:source path="omni-backend/omni-workflow/src/main/java/com/omni/workflow/service/impl/WorkflowModelServiceImpl.java" -->
```java
            deployment = repositoryService.createDeployment()
                    .name(model.getModelName())
                    .category(model.getCategory())
                    .addString(model.getModelKey() + ".bpmn20.xml", bpmnXml)
                    .tenantId(effectiveTenantId)
                    .deploy();
```

XML 리소스·이름·분류·실제 테넌트를 지정합니다. 모델에 분류가 있으면 XML의 targetNamespace도 바꿔 엔진 정의의 분류에 사용합니다.

그다음 정의 ID·배포 ID·업무 버전을 기록하고, 새 버전을 PUBLISHED로 만들며, 이전 게시 버전을 보관 상태로 전환하고 모델의 게시 참조를 갱신합니다. 이 로컬 처리 전체가 성공한 뒤 새 신청은 명확한 버전을 사용해야 합니다.

## 게시 실패 시 응답과 실제 저장 결과를 확인한다

검증 실패 분기는 FAILED를 쓴 뒤 업무 예외를 던집니다. 그러나 같은 롤백 대상 트랜잭션이므로 **FAILED가 반드시 저장되는 것은 아닙니다**. 실패 버전이 남았다고 단정하거나 확인 없이 게시를 연속 클릭하지 마세요.

오류를 바탕으로 XML과 현재 초안을 확인하고, 필요하면 수정해 저장한 뒤 다시 검증합니다. 동시 게시 실패나 시간 초과라면 버전과 게시 참조를 먼저 새로 조회해 이미 성공한 게시가 있는지 확인합니다. 시간 초과만으로 미게시라고 판단할 수 없습니다.

## 원시 정의 관리는 다른 입구다

기존 정의 관리에는 배포·BPMN 조회·중지·활성화·연관 데이터를 포함한 삭제가 남아 있습니다. 위의 업무 초안 검증·버전 게시를 거치지 않습니다. 현재 ID 지정 동작에는 통합 테넌트 소유 검사가 없습니다. definition 기능 권한만으로 데이터 격리를 증명하지 말고, 정상 모델 게시를 대신하는 경로로 쓰지 않도록 합니다.

다음은 [시작과 담당자 찾기](workflow-assignment.kr.md)입니다.
