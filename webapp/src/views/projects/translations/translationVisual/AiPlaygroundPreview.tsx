import { Box, styled, SxProps, Tooltip, useTheme } from '@mui/material';
import { Stars } from 'tg.component/CustomIcons';
import { TranslationVisual } from './TranslationVisual';
import { wrapIf } from 'tg.fixtures/wrapIf';

const StyledAiPreview = styled(Box)`
  display: grid;
  background: ${({ theme }) => theme.palette.tokens.secondary._states.selected};
  grid-template-columns: auto 1fr;
  gap: 4px;
  padding: 8px;
  border-radius: 8px;
`;

const StyledContent = styled('div')`
  display: grid;
`;

type Props = {
  translation: string | undefined;
  contextDescription: string | undefined;
  isPlural: boolean;
  locale: string;
  sx?: SxProps;
};

export const AiPlaygroundPreview = ({
  translation,
  contextDescription,
  isPlural,
  locale,
  sx,
}: Props) => {
  const theme = useTheme();
  return wrapIf(
    contextDescription,
    <StyledAiPreview {...{ sx }}>
      <Stars width={20} height={20} color={theme.palette.secondary.main} />
      <StyledContent>
        <TranslationVisual
          text={translation}
          isPlural={isPlural}
          locale={locale}
          maxLines={3}
          extraPadding={false}
        />
      </StyledContent>
    </StyledAiPreview>,
    // @ts-ignore
    <Tooltip title={contextDescription} />
  );
};
