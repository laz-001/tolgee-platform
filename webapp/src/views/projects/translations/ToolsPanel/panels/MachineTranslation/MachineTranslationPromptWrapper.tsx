import { Box, Button, styled } from '@mui/material';
import { useTranslate } from '@tolgee/react';
import { Stars } from 'tg.component/CustomIcons';
import { QUERY } from 'tg.constants/links';
import { useUrlSearchState } from 'tg.hooks/useUrlSearchState';

const StyledContainer = styled('div')`
  display: grid;
  padding: 20px;
  background: ${({ theme }) => theme.palette.tokens.primary._states.hover};
  border-radius: 8px;
  gap: 4px;
  margin: 0px 8px;
`;

const StyledHeading = styled('div')`
  display: flex;
  justify-content: space-between;
  align-items: center;
`;

const StyledTitle = styled(Box)`
  color: ${({ theme }) => theme.palette.primary.main};
  display: flex;
  gap: 4px;
`;

const StyledTitleText = styled(Box)`
  font-size: 16px;
  font-style: normal;
  font-weight: 600;
  line-height: 143%; /* 22.88px */
  letter-spacing: 0.17px;
`;

type Props = {
  children: React.ReactNode;
};

export const MachineTranslationPromptWrapper = ({ children }: Props) => {
  const [_, setAiPlayground] = useUrlSearchState(
    QUERY.TRANSLATIONS_AI_PLAYGROUND,
    {
      defaultVal: undefined,
      history: false,
    }
  );
  const { t } = useTranslate();
  return (
    <StyledContainer>
      <StyledHeading>
        <StyledTitle>
          <Stars />
          <StyledTitleText>{t('machine_translation_ai_title')}</StyledTitleText>
        </StyledTitle>
        <Box>
          <Button
            color="primary"
            size="small"
            onClick={() => setAiPlayground('1')}
          >
            {t('machine_translation_ai_customize')}
          </Button>
        </Box>
      </StyledHeading>
      <Box>{children}</Box>
    </StyledContainer>
  );
};
