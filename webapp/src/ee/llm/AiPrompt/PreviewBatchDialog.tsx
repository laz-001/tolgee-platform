import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
} from '@mui/material';
import { useTranslate } from '@tolgee/react';
import { useState } from 'react';
import { components } from 'tg.service/apiSchema.generated';
import { useApiMutation } from 'tg.service/http/useQueryApi';
import { BatchOperationsLanguagesSelect } from 'tg.views/projects/translations/BatchOperations/components/BatchOperationsLanguagesSelect';
import { getPreselectedLanguages } from 'tg.views/projects/translations/BatchOperations/getPreselectedLanguages';
import {
  useTranslationsActions,
  useTranslationsSelector,
} from 'tg.views/projects/translations/context/TranslationsContext';

type BatchJobModel = components['schemas']['BatchJobModel'];

type Props = {
  onStart: (data: BatchJobModel) => void;
  onClose: () => void;
  providerName: string;
  template: string;
  projectId: number;
  numberOfKeys: number;
};

export const PreviewBatchDialog = ({
  onStart,
  onClose,
  providerName,
  template,
  projectId,
  numberOfKeys,
}: Props) => {
  const { getAllIds } = useTranslationsActions();
  const { t } = useTranslate();

  const mtTranslate = useApiMutation({
    url: '/v2/projects/{projectId}/start-batch-job/ai-playground-translate',
    method: 'post',
  });

  const allLanguages = useTranslationsSelector((c) => c.languages) || [];
  const translationsLanguages = useTranslationsSelector(
    (c) => c.translationsLanguages
  );
  const languages = allLanguages.filter((l) => !l.base);

  const [selectedLangs, setSelectedLangs] = useState<string[]>(() =>
    getPreselectedLanguages(languages, translationsLanguages ?? [])
  );

  const handleRunBatch = async () => {
    const allIds = await getAllIds();
    mtTranslate
      .mutateAsync({
        content: {
          'application/json': {
            keyIds: allIds,
            targetLanguageIds: allLanguages
              ?.filter((l) => selectedLangs?.includes(l.tag))
              .map((l) => l.id),

            llmPrompt: {
              name: '',
              template,
              providerName,
            },
          },
        },
        path: {
          projectId,
        },
      })
      .then((data) => {
        onStart(data);
        onClose();
      });
  };

  return (
    <Dialog open={true} onClose={onClose}>
      <DialogTitle>
        {t('ai_prompt_batch_dialog_title', { value: numberOfKeys })}
      </DialogTitle>
      <DialogContent>
        <BatchOperationsLanguagesSelect
          languages={allLanguages || []}
          value={selectedLangs || []}
          onChange={setSelectedLangs}
          languagePermission="translations.edit"
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t('global_cancel_button')}</Button>
        <Button onClick={handleRunBatch}>
          {t('confirmation_dialog_confirm')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};
