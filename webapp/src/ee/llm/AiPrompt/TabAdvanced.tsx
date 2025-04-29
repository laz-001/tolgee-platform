import { Box, Link } from '@mui/material';
import { useTranslate } from '@tolgee/react';

import { EditorHandlebars } from 'tg.component/editor/EditorHandlebars';
import { EditorWrapper } from 'tg.component/editor/EditorWrapper';
import { EditorError } from 'tg.component/editor/utils/codemirrorError';
import { stopBubble } from 'tg.fixtures/eventHandler';
import { components } from 'tg.service/apiSchema.generated';
import { Label } from './Label';

type PromptVariable = components['schemas']['PromptVariableDto'];

type Props = {
  value: string;
  onChange: (value: string) => void;
  cellSelected: boolean;
  onRun: () => void;
  availableVariables: PromptVariable[] | undefined;
  errors?: EditorError[];
};

export const TabAdvanced = ({
  value,
  onChange,
  cellSelected,
  onRun,
  availableVariables,
  errors,
}: Props) => {
  const { t } = useTranslate();
  return (
    <Box sx={{ margin: '20px 20px' }}>
      <Label
        rightContent={
          <Link href="https://docs.tolgee.io" target="_blank">
            {t('ai_prompt_learn_more')}
          </Link>
        }
      >
        {t('ai_prompt_label')}
      </Label>
      <EditorWrapper onKeyDown={stopBubble()}>
        <EditorHandlebars
          minHeight={100}
          value={value}
          onChange={onChange}
          unknownVariableMessage={
            cellSelected
              ? t('ai_prompt_editor_unknown_variable')
              : t('ai_prompt_editor_select_translation')
          }
          shortcuts={[
            {
              key: 'Mod-Enter',
              run: () => (onRun(), true),
            },
          ]}
          availableVariables={availableVariables}
          errors={errors}
        />
      </EditorWrapper>
    </Box>
  );
};
