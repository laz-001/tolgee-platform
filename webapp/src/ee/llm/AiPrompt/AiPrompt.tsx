import { useEffect, useState } from 'react';
import {
  Box,
  IconButton,
  MenuItem,
  Select,
  styled,
  Tab,
  Tabs,
} from '@mui/material';
import { useTranslate } from '@tolgee/react';
import { X } from '@untitled-ui/icons-react';

import { useApiMutation, useApiQuery } from 'tg.service/http/useQueryApi';
import { useLocalStorageState } from 'tg.hooks/useLocalStorageState';
import { EditorError } from 'tg.component/editor/utils/codemirrorError';
import { useTranslationsActions } from 'tg.views/projects/translations/context/TranslationsContext';
import { components } from 'tg.service/apiSchema.generated';
import { DeletableKeyWithTranslationsModelType } from 'tg.views/projects/translations/context/types';
import { useUrlSearchState } from 'tg.hooks/useUrlSearchState';
import { QUERY } from 'tg.constants/links';

import { AiResult } from './AiResult';
import { PromptLoadMenu } from './PromptLoadMenu';
import { PromptPreviewMenu } from './PromptPreviewMenu';
import { PromptSaveMenu } from './PromptSaveMenu';
import { TabAdvanced } from './TabAdvanced';
import { AiResultUsage } from './AiResultUsage';
import { AiRenderedPrompt } from './AiRenderedPrompt';

type ProjectModel = components['schemas']['ProjectModel'];
type LanguageModel = components['schemas']['LanguageModel'];

const StyledContainer = styled('div')`
  display: grid;
  height: 100%;
  max-height: 100%;
  grid-template-rows: 1fr auto;
  overflow: auto;
`;

const StyledMainContent = styled('div')`
  display: grid;
  align-self: start;
  margin-bottom: 50px;
`;

const StyledHeader = styled('div')`
  display: grid;
  margin: 16px 20px 0px 20px;
  border-bottom: 1px solid ${({ theme }) => theme.palette.divider};
  gap: 16px;
`;

const StyledTitle = styled('div')`
  display: flex;
  align-items: center;
  justify-content: space-between;
`;

const StyledTitleText = styled('div')`
  font-size: 20px;
  font-weight: 400;
`;

const StyledTab = styled(Tab)`
  padding: 9px 16px;
  min-height: 42px;
`;

const StyledTabs = styled(Tabs)`
  margin-bottom: -1px;
  min-height: unset;
`;

const StyledActionsWrapper = styled('div')`
  padding: 12px 20px;
  display: flex;
  gap: 8px;
  justify-content: space-between;
  align-items: end;
  position: sticky;
  bottom: 0px;
  background: ${({ theme }) => theme.palette.background.default};
  box-shadow: 0px -4px 6px 0px rgba(0, 0, 0, 0.08);
`;

type Props = {
  width: number;
  project: ProjectModel;
  language: LanguageModel;
  keyData: DeletableKeyWithTranslationsModelType;
};

export const AiPrompt: React.FC<Props> = (props) => {
  const projectId = props.project.id;
  const { t } = useTranslate();
  const { refetchTranslations } = useTranslationsActions();
  const [_, setAiPlayground] = useUrlSearchState(
    QUERY.TRANSLATIONS_AI_PLAYGROUND,
    {
      defaultVal: undefined,
      history: false,
    }
  );
  const [tab, setTab] = useLocalStorageState({
    key: `aiPlaygroundTab-${projectId}`,
    initial: 'advanced',
  });
  const [lastOpenPrompt, setLastOpenPrompt] = useLocalStorageState({
    key: `aiPlaygroundLastPrompt-${projectId}`,
    initial: undefined,
  });
  const [value, setValue] = useLocalStorageState<string>({
    key: `aiPlaygroundLastValue-${projectId}`,
    initial: 'Hi translate from {{source}} to {{target}}',
  });

  const [provider, setProvider] = useLocalStorageState<string>({
    key: `aiPlaygroundProvider-${projectId}`,
    initial: 'default',
  });
  const [errors, setErrors] = useState<EditorError[]>();

  const promptLoadable = useApiMutation({
    url: '/v2/projects/{projectId}/prompts/run',
    method: 'post',
    invalidatePrefix: '/v2/projects/{projectId}/ai-playground-result',
  });

  useEffect(() => {
    setErrors(undefined);
  }, [value]);

  const lastPrompt = useApiQuery({
    url: '/v2/projects/{projectId}/prompts/{promptId}',
    method: 'get',
    path: {
      projectId,
      promptId: Number(lastOpenPrompt)!,
    },
    options: {
      enabled: Boolean(lastOpenPrompt),
    },
  });

  const providersLoadable = useApiQuery({
    url: '/v2/organizations/{organizationId}/llm-providers/all-available',
    method: 'get',
    path: {
      organizationId: props.project.organizationOwner!.id,
    },
  });

  const cellSelected = Boolean(props.keyData && props.language);

  const lastPromptName =
    lastPrompt.data?.name ?? t('ai_prompt_default_prompt_name');

  const promptVariables = useApiQuery({
    url: '/v2/projects/{projectId}/prompts/get-variables',
    method: 'get',
    path: {
      projectId: props.project.id,
    },
    query: {
      keyId: props.keyData?.keyId,
      targetLanguageId: props.language?.id,
    },
  });

  function handleTestPrompt() {
    if (!cellSelected) {
      return;
    }
    promptLoadable.mutate(
      {
        path: {
          projectId: props.project.id,
        },
        content: {
          'application/json': {
            template: value,
            keyId: props.keyData.keyId,
            targetLanguageId: props.language.id,
            provider,
          },
        },
      },
      {
        onError(e) {
          if (e.code === 'llm_template_parsing_error' && e.params) {
            setErrors([
              {
                message: e.params[0],
                line: e.params[1],
                column: e.params[2] + 1,
              },
            ]);
          }
          e.handleError?.();
        },
      }
    );
  }

  const usage = promptLoadable.data?.usage;

  return (
    <StyledContainer>
      <StyledMainContent>
        <StyledHeader>
          <StyledTitle>
            <StyledTitleText>{lastPromptName}</StyledTitleText>
            <Box display="flex" alignItems="center">
              <PromptLoadMenu
                projectId={props.project.id}
                onSelect={(item) => {
                  setLastOpenPrompt(
                    item.id !== undefined ? String(item.id) : undefined
                  );
                  setProvider(item.providerName);
                  setValue(item.template);
                }}
              />
              <IconButton onClick={() => setAiPlayground(undefined)}>
                <X />
              </IconButton>
            </Box>
          </StyledTitle>
          <Select
            size="small"
            value={provider}
            onChange={(e) => setProvider(e.target.value)}
            sx={{ width: '50%' }}
          >
            {providersLoadable.data?.items.map((i) => (
              <MenuItem key={i.name} value={i.name}>
                {i.name}
              </MenuItem>
            ))}
          </Select>
          <StyledTabs value={tab} onChange={(_, value) => setTab(value)}>
            <StyledTab label={t('ai_prompt_tab_basic')} value="basic" />
            <StyledTab label={t('ai_prompt_tab_advanced')} value="advanced" />
          </StyledTabs>
        </StyledHeader>

        {tab === 'advanced' ? (
          <TabAdvanced
            value={value}
            onChange={setValue}
            onRun={handleTestPrompt}
            availableVariables={promptVariables.data?.data}
            errors={errors}
            cellSelected={cellSelected}
          />
        ) : null}

        <Box sx={{ margin: '20px', display: 'grid', gap: 1.5 }}>
          <AiResult
            raw={promptLoadable.data?.result}
            json={promptLoadable.data?.parsedJson}
            isPlural={props.keyData?.keyIsPlural}
            locale={props.language?.tag}
          />
          <AiResultUsage usage={usage} price={promptLoadable.data?.price} />
        </Box>

        <Box sx={{ margin: '20px', display: 'grid' }}>
          <AiRenderedPrompt data={promptLoadable.data?.prompt} />
        </Box>
      </StyledMainContent>
      <StyledActionsWrapper>
        <PromptPreviewMenu
          projectId={projectId}
          languageId={props.language?.id}
          templateValue={value}
          providerName={provider}
          onBatchFinished={() => {
            refetchTranslations();
          }}
          onTestPrompt={handleTestPrompt}
          loading={promptLoadable.isLoading}
        />
        <PromptSaveMenu
          projectId={projectId}
          data={{
            template: value,
            providerName: provider,
          }}
          existingPrompt={lastPrompt.data}
        />
      </StyledActionsWrapper>
    </StyledContainer>
  );
};
