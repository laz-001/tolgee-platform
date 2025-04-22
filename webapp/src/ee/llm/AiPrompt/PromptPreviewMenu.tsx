import { useRef, useState } from 'react';
import {
  Button,
  ButtonGroup,
  Menu,
  MenuItem,
  styled,
  Tooltip,
} from '@mui/material';
import { useTranslate } from '@tolgee/react';

import LoadingButton from 'tg.component/common/form/LoadingButton';
import { ArrowDropDown, Stars } from 'tg.component/CustomIcons';
import { confirmation } from 'tg.hooks/confirmation';
import { useApiMutation } from 'tg.service/http/useQueryApi';
import { BatchOperationDialog } from 'tg.views/projects/translations/BatchOperations/OperationsSummary/BatchOperationDialog';
import { BatchJobModel } from 'tg.views/projects/translations/BatchOperations/types';
import {
  useTranslationsActions,
  useTranslationsSelector,
} from 'tg.views/projects/translations/context/TranslationsContext';

const StyledArrowButton = styled(Button)`
  padding-left: 6px;
  padding-right: 6px;
  min-width: unset !important;
`;

type Props = {
  languageId: number | undefined;
  projectId: number;
  templateValue: string;
  providerName: string;
  onBatchFinished: () => void;
  onTestPrompt: () => void;
  loading?: boolean;
};

export const PromptPreviewMenu = ({
  languageId,
  projectId,
  templateValue,
  providerName,
  onBatchFinished,
  onTestPrompt,
  loading,
}: Props) => {
  const [open, setOpen] = useState(false);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const { t } = useTranslate();
  const translationsTotal = useTranslationsSelector((c) => c.translationsTotal);

  const disabled = languageId === undefined;

  const [runningOperation, setRunningOperation] = useState<BatchJobModel>();

  const mtTranslate = useApiMutation({
    url: '/v2/projects/{projectId}/start-batch-job/ai-playground-translate',
    method: 'post',
  });

  const { getAllIds, setEdit } = useTranslationsActions();

  const handleRunBatch = async () => {
    const allIds = await getAllIds();
    confirmation({
      title: `Run for ${allIds.length} keys?`,
      onConfirm() {
        setEdit(undefined);
        mtTranslate
          .mutateAsync({
            content: {
              'application/json': {
                keyIds: allIds,
                targetLanguageIds: [languageId!],
                llmPrompt: {
                  name: '',
                  template: templateValue,
                  providerName,
                },
              },
            },
            path: {
              projectId,
            },
          })
          .then((data) => {
            setRunningOperation(data);
          });
      },
    });
  };

  return (
    <>
      <ButtonGroup variant="contained" color="secondary" size="small">
        <Tooltip
          enterDelay={1000}
          title={
            disabled
              ? t('ai_prompt_preview_disabled_hint')
              : t('ai_prompt_preview_hint')
          }
          disableInteractive
        >
          <span>
            <LoadingButton
              disabled={disabled}
              loading={loading}
              startIcon={<Stars height={18} />}
              ref={buttonRef}
              onClick={onTestPrompt}
            >
              {t('ai_prompt_preview_label')}
            </LoadingButton>
          </span>
        </Tooltip>
        <StyledArrowButton onClick={() => setOpen(true)} ref={buttonRef as any}>
          <ArrowDropDown />
        </StyledArrowButton>
      </ButtonGroup>

      {open && (
        <Menu
          open={true}
          onClose={() => setOpen(false)}
          anchorEl={buttonRef.current}
          MenuListProps={{ sx: { minWidth: 250 } }}
          anchorOrigin={{ horizontal: 'right', vertical: 'top' }}
          transformOrigin={{ horizontal: 'right', vertical: 'bottom' }}
        >
          <MenuItem
            onClick={() => {
              setOpen(false);
            }}
          >
            {t('ai_prompt_preview_on_dataset')}
          </MenuItem>
          <MenuItem
            onClick={() => {
              setOpen(false);
              handleRunBatch();
            }}
          >
            {t('ai_prompt_preview_on_all', { value: translationsTotal })}
          </MenuItem>
        </Menu>
      )}

      {runningOperation && (
        <BatchOperationDialog
          operation={runningOperation}
          onClose={() => setRunningOperation(undefined)}
          onFinished={() => {
            setRunningOperation(undefined);
            onBatchFinished();
          }}
        />
      )}
    </>
  );
};
