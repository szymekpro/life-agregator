from pydantic import BaseModel


class WelcomeResponse(BaseModel):
    name: str
    status: str
    docs: str
    endpoints: list[str]


class HealthResponse(BaseModel):
    status: str


class FitatuTokenResponse(BaseModel):
    status: str
    fitatu_user_id: str | None = None
    error: str | None = None
    status_code: int | None = None
    details: str | dict | list | None = None


class CaloriesInfo(BaseModel):
    consumed: float
    target: float | None = None


class MacrosInfo(BaseModel):
    protein: float
    fat: float
    carbohydrate: float
    fiber: float
    sugars: float
    salt: float


class MealItemInfo(BaseModel):
    name: str
    calories: float
    protein: float
    fat: float
    carbohydrate: float
    fiber: float
    eaten: bool


class MealInfo(BaseModel):
    name: str
    item_count: int
    calories: float
    macros: MacrosInfo
    items: list[MealItemInfo]


class FitatuDailyData(BaseModel):
    date: str
    calories: CaloriesInfo
    macros: MacrosInfo
    meals: list[MealInfo]


class FitatuDailyResponse(BaseModel):
    status: str
    data: FitatuDailyData | None = None
    error: str | None = None
    status_code: int | None = None
    details: str | dict | list | None = None


class FitatuPlanData(BaseModel):
    date: str
    goal_label: str | None = None
    calories_target: float | None = None
    start_weight_kg: float | None = None
    current_weight_kg: float | None = None
    target_weight_kg: float | None = None
    weight_change_pace_kg: float | None = None
    activity_training: float | None = None


class FitatuPlanResponse(BaseModel):
    status: str
    data: FitatuPlanData | None = None
    error: str | None = None
    status_code: int | None = None
    details: str | dict | list | None = None


class FitatuTargetsMacros(BaseModel):
    protein_g: float | None = None
    fat_g: float | None = None
    carbohydrate_g: float | None = None
    protein_percent: float | None = None
    fat_percent: float | None = None
    carbohydrate_percent: float | None = None


class FitatuTargetsData(BaseModel):
    date: str
    calories: float | None = None
    macros: FitatuTargetsMacros | None = None


class FitatuTargetsResponse(BaseModel):
    status: str
    data: FitatuTargetsData | None = None
    error: str | None = None
    status_code: int | None = None
    details: str | dict | list | None = None
