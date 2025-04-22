import { useRef, useState } from 'react';
import {
  Box,
  Button,
  ButtonGroup,
  Dialog,
  DialogActions,
  DialogContent,
  Menu,
  MenuItem,
  styled,
  TextField,
} from '@mui/material';
import { useTranslate } from '@tolgee/react';

import LoadingButton from 'tg.component/common/form/LoadingButton';
import { components } from 'tg.service/apiSchema.generated';
import { useApiMutation } from 'tg.service/http/useQueryApi';
import { ArrowDropDown } from 'tg.component/CustomIcons';

type PromptModel = components['schemas']['PromptModel'];

const StyledArrowButton = styled(Button)`
  padding-left: 6px;
  padding-right: 6px;
  min-width: unset !important;
`;

type Props = {
  projectId: number;
  data: Omit<PromptModel, 'projectId' | 'id' | 'name'>;
  existingPrompt?: PromptModel;
};

export const PromptSaveMenu = ({ projectId, data, existingPrompt }: Props) => {
  const [open, setOpen] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [name, setName] = useState('');
  const { t } = useTranslate();

  const buttonRef = useRef<HTMLButtonElement>(null);

  const createPrompt = useApiMutation({
    url: '/v2/projects/{projectId}/prompts',
    method: 'post',
    invalidatePrefix: '/v2/projects/{projectId}/prompts',
  });

  const updatePrompt = useApiMutation({
    url: '/v2/projects/{projectId}/prompts/{promptId}',
    method: 'put',
    invalidatePrefix: '/v2/projects/{projectId}/prompts',
  });

  return (
    <Box>
      {existingPrompt ? (
        <ButtonGroup variant="contained" size="small" color="primary">
          <LoadingButton
            loading={updatePrompt.isLoading}
            onClick={() => {
              updatePrompt.mutate({
                path: { projectId, promptId: existingPrompt.id },
                content: {
                  'application/json': { ...data, name: existingPrompt.name },
                },
              });
            }}
          >
            {t('ai_prompt_save_label')}
          </LoadingButton>
          <StyledArrowButton
            onClick={() => setOpen(true)}
            ref={buttonRef as any}
          >
            <ArrowDropDown />
          </StyledArrowButton>
        </ButtonGroup>
      ) : (
        <LoadingButton
          variant="contained"
          size="small"
          color="primary"
          ref={buttonRef}
          onClick={() => setCreateOpen(true)}
        >
          {t('ai_prompt_save_as_new_label')}
        </LoadingButton>
      )}
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
              setCreateOpen(true);
            }}
          >
            {t('ai_prompt_save_as_new_label')}
          </MenuItem>
        </Menu>
      )}
      {createOpen && (
        <Dialog
          open={true}
          onClose={() => {
            setName('');
            setCreateOpen(false);
          }}
        >
          <DialogContent>
            <TextField value={name} onChange={(e) => setName(e.target.value)} />
          </DialogContent>
          <DialogActions>
            <Button
              onClick={() => {
                createPrompt.mutate(
                  {
                    path: { projectId },
                    content: { 'application/json': { ...data, name } },
                  },
                  {
                    onSuccess() {
                      setCreateOpen(false);
                    },
                  }
                );
              }}
            >
              Save
            </Button>
          </DialogActions>
        </Dialog>
      )}
    </Box>
  );
};
