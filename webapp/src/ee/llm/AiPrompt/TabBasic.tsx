import {
  Alert,
  Box,
  FormControlLabel,
  IconButton,
  Switch,
  Tooltip,
  useTheme,
} from '@mui/material';
import { T, useTranslate } from '@tolgee/react';
import { InfoCircle, X } from '@untitled-ui/icons-react';

import { Label } from './Label';
import { useLocalStorageState } from 'tg.hooks/useLocalStorageState';
import { components } from 'tg.service/apiSchema.generated';
import React from 'react';

export type BasicPromptOption = NonNullable<
  components['schemas']['PromptRunDto']['options']
>[number];

type PromptItem = {
  id: BasicPromptOption;
  label: React.ReactNode;
  hint: React.ReactNode;
};

const basicPromptItems: PromptItem[] = [
  {
    id: 'KEY_CONTEXT',
    label: <T keyName="ai_prompt_item_key_context" />,
    hint: <T keyName="ai_prompt_item_key_context_hint" />,
  },
  {
    id: 'PROJECT_DESCRIPTION',
    label: <T keyName="ai_prompt_item_project_description" />,
    hint: <T keyName="ai_prompt_item_project_description_hint" />,
  },
  {
    id: 'LANGUAGE_NOTES',
    label: <T keyName="ai_prompt_item_language_notes" />,
    hint: <T keyName="ai_prompt_item_language_notes_hint" />,
  },
  {
    id: 'TM_SUGGESTIONS',
    label: <T keyName="ai_prompt_item_tm_suggestions" />,
    hint: <T keyName="ai_prompt_item_tm_suggestions_hint" />,
  },
  {
    id: 'SCREENSHOTS',
    label: <T keyName="ai_prompt_item_screenshots" />,
    hint: <T keyName="ai_prompt_item_screenshots_hint" />,
  },
];

type Props = {
  value: BasicPromptOption[];
  onChange: (value: BasicPromptOption[]) => void;
};

export const TabBasic = ({ value, onChange }: Props) => {
  const theme = useTheme();
  const { t } = useTranslate();
  const [hideTip, setHideTip] = useLocalStorageState({
    key: 'aiPlaygroundHideBasicTip',
    initial: undefined,
  });

  return (
    <Box sx={{ margin: '20px' }}>
      <Label>{t('ai_prompt_basic_label')}</Label>
      {!hideTip && (
        <Alert
          severity="info"
          icon={false}
          sx={{ mb: 2 }}
          action={
            <IconButton size="small" onClick={() => setHideTip('true')}>
              <X width={20} height={20} />
            </IconButton>
          }
        >
          {t('ai_playground_basic_tip')}
        </Alert>
      )}
      <Box display="grid" sx={{ gap: '20px' }}>
        {basicPromptItems.map(({ id, label, hint }) => {
          const checked = value.includes(id);
          return (
            <Box
              key={id}
              display="flex"
              justifyContent="space-between"
              alignItems="center"
              marginRight={1}
            >
              <FormControlLabel
                control={<Switch size="small" />}
                label={label}
                sx={{ marginLeft: 0, gap: 0.5 }}
                checked={checked}
                onChange={() => {
                  if (checked) {
                    onChange(value.filter((i) => i !== id));
                  } else {
                    onChange([...value, id]);
                  }
                }}
              />
              <Tooltip title={hint} disableInteractive>
                <span>
                  <InfoCircle
                    width={20}
                    height={20}
                    color={theme.palette.tokens.icon.secondary}
                  />
                </span>
              </Tooltip>
            </Box>
          );
        })}
      </Box>
    </Box>
  );
};
