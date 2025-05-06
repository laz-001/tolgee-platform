import { useRef, useState } from 'react';
import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  SxProps,
} from '@mui/material';
import { useTranslate } from '@tolgee/react';
import { Edit01 } from '@untitled-ui/icons-react';

import { components } from 'tg.service/apiSchema.generated';
import { Formik } from 'formik';
import { TextField } from 'tg.component/common/form/fields/TextField';
import { useApiMutation } from 'tg.service/http/useQueryApi';
import LoadingButton from 'tg.component/common/form/LoadingButton';
import { Validation } from 'tg.constants/GlobalValidationSchema';

type PromptDto = components['schemas']['PromptDto'];
type PromptModel = components['schemas']['PromptModel'];

export type PromptItem = PromptDto & { id?: number };

type Props = {
  className?: string;
  sx?: SxProps;
  data: PromptModel;
  projectId: number;
};

export const PromptRename = ({ className, sx, data, projectId }: Props) => {
  const [open, setOpen] = useState(false);
  const { t } = useTranslate();
  const buttonRef = useRef<HTMLButtonElement>(null);

  const updateLoadable = useApiMutation({
    url: '/v2/projects/{projectId}/prompts/{promptId}',
    method: 'put',
    invalidatePrefix: '/v2/projects/{projectId}/prompts',
  });

  return (
    <>
      <IconButton ref={buttonRef} onClick={() => setOpen(true)}>
        <Edit01 width={20} height={20} {...{ className, sx }} />
      </IconButton>
      {open && (
        <Dialog open={true} onClose={() => setOpen(false)}>
          <Formik
            initialValues={{ name: data.name }}
            validationSchema={Validation.PROMPT_RENAME()}
            onSubmit={(values) => {
              updateLoadable.mutate(
                {
                  path: { projectId, promptId: data.id },
                  content: {
                    'application/json': { ...data, name: values.name },
                  },
                },
                {
                  onSuccess() {
                    setOpen(false);
                  },
                }
              );
            }}
          >
            {({ handleSubmit, values }) => (
              <>
                <DialogTitle>{t('ai_prompt_rename_title')}</DialogTitle>
                <DialogContent sx={{ minWidth: 300 }}>
                  <TextField
                    name="name"
                    fullWidth
                    label={t('ai_prompt_rename_name_field')}
                  />
                </DialogContent>
                <DialogActions>
                  <Button onClick={() => setOpen(false)}>
                    {t('global_cancel_button')}
                  </Button>
                  <LoadingButton
                    loading={updateLoadable.isLoading}
                    disabled={values.name === data.name}
                    onClick={() => handleSubmit()}
                    color="primary"
                  >
                    {t('global_form_save')}
                  </LoadingButton>
                </DialogActions>
              </>
            )}
          </Formik>
        </Dialog>
      )}
    </>
  );
};
